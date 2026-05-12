import React, { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ChevronLeft, ChevronRight, Home, LogOut, Plus, Search, Trash2, Upload, X } from 'lucide-react';
import {
  addNote,
  addPayment,
  createClient,
  createProject,
  deleteClient,
  deleteNote,
  deletePayment,
  deleteProject,
  deleteSketch,
  getDashboardStats,
  getClient,
  isLoggedIn,
  listClients,
  login,
  logout,
  sketchUrl,
  updateFee,
  updateProject,
  uploadSketch,
} from './lib/api';
import { inr, label, today } from './lib/format';
import type { ClientDetail, ClientSummary, DashboardStats, PaymentMode, PaymentStage, Project, ProjectStatus, ProjectType, RecentProjectItem, Sketch } from './types';
import type { ProjectPayload } from './lib/api';
import './styles.css';

const projectTypes: ProjectType[] = ['RESIDENTIAL', 'COMMERCIAL', 'RENOVATION'];
const projectStatuses: ProjectStatus[] = ['ACTIVE', 'ON_HOLD', 'COMPLETED'];
const paymentModes: PaymentMode[] = ['CASH', 'BANK_TRANSFER', 'UPI', 'CHEQUE'];
const paymentStages: PaymentStage[] = ['TOKEN', 'PROGRESS', 'FINAL'];

type ProjectDraft = ReturnType<typeof projectDefaults> & { clientId: number };
type WorkspaceTab = 'requirements' | 'meeting_notes' | 'site_sketches';
type PaymentTab = 'payments' | 'history';
type WorkspaceMode = 'new_client' | 'new_project' | 'project';

interface StoredWorkspaceState {
  selectedClientId: number | null;
  selectedProjectId: number | null;
  query: string;
  mode: WorkspaceMode;
  workspaceTab: WorkspaceTab;
  paymentTab: PaymentTab;
}

const workspaceStorageKey = 'archdesk.workspace.v2';

function defaultWorkspaceState(): StoredWorkspaceState {
  return {
    selectedClientId: null,
    selectedProjectId: null,
    query: '',
    mode: 'project',
    workspaceTab: 'requirements',
    paymentTab: 'payments',
  };
}

function readWorkspaceState(): StoredWorkspaceState {
  try {
    const raw = localStorage.getItem(workspaceStorageKey);
    if (!raw) return defaultWorkspaceState();
    const parsed = JSON.parse(raw) as Partial<StoredWorkspaceState>;
    return {
      selectedClientId: typeof parsed.selectedClientId === 'number' ? parsed.selectedClientId : null,
      selectedProjectId: typeof parsed.selectedProjectId === 'number' ? parsed.selectedProjectId : null,
      query: typeof parsed.query === 'string' ? parsed.query : '',
      mode: parsed.mode === 'new_client' || parsed.mode === 'new_project' ? parsed.mode : 'project',
      workspaceTab: parsed.workspaceTab === 'meeting_notes' || parsed.workspaceTab === 'site_sketches' ? parsed.workspaceTab : 'requirements',
      paymentTab: parsed.paymentTab === 'history' ? parsed.paymentTab : 'payments',
    };
  } catch {
    return defaultWorkspaceState();
  }
}

function writeWorkspaceState(state: StoredWorkspaceState) {
  localStorage.setItem(workspaceStorageKey, JSON.stringify(state));
}

function clearWorkspaceState() {
  localStorage.removeItem(workspaceStorageKey);
}

function clientDefaults() {
  return {
    fullName: '',
    phoneNumber: '',
    emailAddress: '',
    addressLocality: '',
    defaultProjectType: 'RESIDENTIAL' as ProjectType,
    plotSize: '',
    projectStatus: 'ACTIVE' as ProjectStatus,
    generalNotes: '',
  };
}

function projectDefaults(client?: ClientDetail) {
  return {
    name: client ? `${client.fullName} project` : '',
    projectType: client?.defaultProjectType ?? 'RESIDENTIAL' as ProjectType,
    status: client?.projectStatus ?? 'ACTIVE' as ProjectStatus,
    plotSize: client?.plotSize ?? '',
    requirements: '',
    notes: '',
    startDate: '',
    expectedCompletion: '',
    actualCompletion: '',
  };
}

export default function App() {
  const [authenticated, setAuthenticated] = useState(isLoggedIn());

  useEffect(() => {
    function handleAuthExpired() {
      clearWorkspaceState();
      setAuthenticated(false);
    }
    window.addEventListener('archdesk.auth.expired', handleAuthExpired);
    return () => window.removeEventListener('archdesk.auth.expired', handleAuthExpired);
  }, []);

  return authenticated ? (
    <Workspace onLogout={() => {
      clearWorkspaceState();
      logout();
      setAuthenticated(false);
    }}
    />
  ) : (
    <Login onLogin={() => setAuthenticated(true)} />
  );
}

function Login({ onLogin }: { onLogin: () => void }) {
  const [email, setEmail] = useState('admin@archdesk.local');
  const [password, setPassword] = useState('admin123');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      await login(email, password);
      onLogin();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-page">
      <form className="login-card" onSubmit={submit}>
        <strong className="login-title">ArchDesk</strong>
        <label>Email<input value={email} onChange={(event) => setEmail(event.target.value)} /></label>
        <label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>
        {error && <p className="error">{error}</p>}
        <button className="primary" disabled={loading}>{loading ? 'Signing in...' : 'Sign in'}</button>
      </form>
    </main>
  );
}

function Workspace({ onLogout }: { onLogout: () => void }) {
  const initialWorkspace = useMemo(() => readWorkspaceState(), []);
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [selectedClientId, setSelectedClientId] = useState<number | null>(initialWorkspace.selectedClientId);
  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(initialWorkspace.selectedProjectId);
  const [detail, setDetail] = useState<ClientDetail | null>(null);
  const [query, setQuery] = useState(initialWorkspace.query);
  const [message, setMessage] = useState('');
  const [showNewClient, setShowNewClient] = useState(initialWorkspace.mode === 'new_client');
  const [showNewProject, setShowNewProject] = useState(initialWorkspace.mode === 'new_project');
  const [projectDraft, setProjectDraft] = useState<ProjectDraft | null>(null);
  const [workspaceTab, setWorkspaceTab] = useState<WorkspaceTab>(initialWorkspace.workspaceTab);
  const [paymentTab, setPaymentTab] = useState<PaymentTab>(initialWorkspace.paymentTab);

  const selectedProject = useMemo(
    () => {
      if (showNewClient || showNewProject) return null;
      return detail?.projects.find((project) => project.id === selectedProjectId) ?? detail?.projects[0] ?? null;
    },
    [detail, selectedProjectId, showNewClient, showNewProject],
  );

  const visibleDraft = detail && projectDraft?.clientId === detail.id ? projectDraft : null;

  async function refresh(clientId = selectedClientId, projectId = selectedProjectId, searchText = query) {
    const params = new URLSearchParams();
    if (searchText.trim().length >= 2) params.set('search', searchText.trim());
    params.set('sort', 'created_desc');
    setClients(await listClients(params));

    if (!clientId) return;
    let fresh: ClientDetail;
    try {
      fresh = await getClient(clientId);
    } catch (err) {
      setDetail(null);
      setSelectedClientId(null);
      setSelectedProjectId(null);
      setShowNewClient(false);
      setShowNewProject(false);
      clearWorkspaceState();
      throw err;
    }
    setDetail(fresh);
    setSelectedClientId(fresh.id);
    const nextProject = fresh.projects.find((project) => project.id === projectId) ?? fresh.projects[0] ?? null;
    setSelectedProjectId(nextProject?.id ?? null);
    if (showNewProject) {
      setProjectDraft((draft) => (draft?.clientId === fresh.id ? draft : { ...projectDefaults(fresh), clientId: fresh.id }));
    }
  }

  function workspaceMode(): WorkspaceMode {
    if (showNewClient) return 'new_client';
    if (showNewProject) return 'new_project';
    return 'project';
  }

  useEffect(() => {
    const handle = window.setTimeout(() => refresh().catch((err) => setMessage(err.message)), 250);
    return () => window.clearTimeout(handle);
  }, [query]);

  useEffect(() => {
    writeWorkspaceState({
      selectedClientId,
      selectedProjectId,
      query,
      mode: workspaceMode(),
      workspaceTab,
      paymentTab,
    });
  }, [selectedClientId, selectedProjectId, query, showNewClient, showNewProject, workspaceTab, paymentTab]);

  async function selectClient(id: number) {
    const fresh = await getClient(id);
    setDetail(fresh);
    setSelectedClientId(id);
    setSelectedProjectId(fresh.projects[0]?.id ?? null);
    setShowNewClient(false);
    setShowNewProject(false);
    setWorkspaceTab('requirements');
    setPaymentTab('payments');
  }

  function openProjectDraft() {
    if (!detail) return;
    setProjectDraft((draft) => (draft?.clientId === detail.id ? draft : { ...projectDefaults(detail), clientId: detail.id }));
    setShowNewClient(false);
    setShowNewProject(true);
    setSelectedProjectId(null);
    setWorkspaceTab('requirements');
    setPaymentTab('payments');
  }

  function selectProject(id: number) {
    setShowNewClient(false);
    setShowNewProject(false);
    setSelectedProjectId(id);
    setWorkspaceTab('requirements');
    setPaymentTab('payments');
  }

  function goHome() {
    clearWorkspaceState();
    setDetail(null);
    setSelectedClientId(null);
    setSelectedProjectId(null);
    setQuery('');
    setShowNewClient(false);
    setShowNewProject(false);
    setProjectDraft(null);
    setWorkspaceTab('requirements');
    setPaymentTab('payments');
    refresh(null, null, '').catch((err) => setMessage(err.message));
  }

  async function removeClient() {
    if (!detail || !window.confirm('Permanently delete this client and all projects?')) return;
    await deleteClient(detail.id);
    setDetail(null);
    setSelectedClientId(null);
    setSelectedProjectId(null);
    setShowNewClient(false);
    setProjectDraft(null);
    await refresh(null, null);
  }

  return (
    <main className={selectedProject && !showNewProject ? 'dashboard-shell' : 'dashboard-shell no-payment'}>
      <header className="mock-topbar">
        <strong>DASHBOARD</strong>
        <div className="top-search"><Search size={14} /><input placeholder="enter client name or number" value={query} onChange={(event) => setQuery(event.target.value)} /></div>
        <button className="home-button" onClick={goHome}>
          <Home size={15} /> Home
        </button>
        <button
          className="top-action"
          onClick={() => {
            setShowNewClient(true);
            setDetail(null);
            setSelectedClientId(null);
            setSelectedProjectId(null);
            setShowNewProject(false);
            setWorkspaceTab('requirements');
            setPaymentTab('payments');
          }}
        >
          <Plus size={15} /> New Client
        </button>
        <button className="logout-button" title="Logout" onClick={onLogout}><LogOut size={16} /> Logout</button>
      </header>
      {message && <button className="notice" onClick={() => setMessage('')}>{message}</button>}
      <section className="mock-grid">
        <ClientSidebar
          clients={clients}
          selectedClient={detail}
          selectedProjectId={selectedProjectId}
          showNewProject={showNewProject}
          projectDraft={visibleDraft}
          onSelectClient={selectClient}
          onSelectProject={selectProject}
          onDeleteClient={removeClient}
          onOpenProjectDraft={openProjectDraft}
        />
        <RequirementPane
          client={detail}
          project={selectedProject}
          showNewClient={showNewClient}
          showNewProject={showNewProject}
          projectDraft={visibleDraft}
          activeTab={workspaceTab}
          onClientSaved={async (client) => {
            setShowNewClient(false);
            setDetail(client);
            setSelectedClientId(client.id);
            setWorkspaceTab('requirements');
            setPaymentTab('payments');
            await refresh(client.id, null);
          }}
          onTabChange={setWorkspaceTab}
          onProjectDraftChange={setProjectDraft}
          onProjectDraftDiscard={() => {
            setProjectDraft(null);
            setShowNewProject(false);
          }}
          onProjectSaved={async (project) => {
            setProjectDraft(null);
            setShowNewProject(false);
            setSelectedProjectId(project.id);
            setWorkspaceTab('requirements');
            setPaymentTab('payments');
            await refresh(detail?.id ?? null, project.id);
          }}
          onProjectUpdated={async () => refresh(detail?.id ?? null, selectedProjectId)}
          onProjectDeleted={async () => refresh(detail?.id ?? null, null)}
          onNavigateToProject={async (clientId, projectId) => {
            const fresh = await getClient(clientId);
            setDetail(fresh);
            setSelectedClientId(clientId);
            setSelectedProjectId(projectId);
            setShowNewClient(false);
            setShowNewProject(false);
            setWorkspaceTab('requirements');
            setPaymentTab('payments');
          }}
        />
        {selectedProject && !showNewProject && (
          <PaymentSidebar
            project={selectedProject}
            activeTab={paymentTab}
            onTabChange={setPaymentTab}
            refresh={() => refresh(detail?.id ?? null, selectedProject?.id ?? null)}
          />
        )}
      </section>
    </main>
  );
}

function ClientSidebar({
  clients,
  selectedClient,
  selectedProjectId,
  showNewProject,
  projectDraft,
  onSelectClient,
  onSelectProject,
  onDeleteClient,
  onOpenProjectDraft,
}: {
  clients: ClientSummary[];
  selectedClient: ClientDetail | null;
  selectedProjectId: number | null;
  showNewProject: boolean;
  projectDraft: ProjectDraft | null;
  onSelectClient: (id: number) => void;
  onSelectProject: (id: number) => void;
  onDeleteClient: () => void;
  onOpenProjectDraft: () => void;
}) {
  return (
    <aside className="left-rail">
      {selectedClient && (
        <div className="client-profile-block">
          <div className="rail-heading">Client Profile</div>
          <strong>{selectedClient.fullName}</strong>
          <span>{selectedClient.phoneNumber}</span>
          <span>{selectedClient.addressLocality || 'No locality'}</span>
          <span>{label(selectedClient.projectStatus)} · {selectedClient.projects.length} project(s)</span>
          <button className="rail-link danger-link" onClick={onDeleteClient}>delete client</button>
        </div>
      )}

      {selectedClient && (
        <div className="project-nav">
          <div className="rail-heading section-heading">Projects</div>
          <button className="rail-action" onClick={onOpenProjectDraft}><Plus size={13} /> New Project</button>
          {projectDraft && (
            <button
              className={showNewProject ? 'project-tab draft-tab active' : 'project-tab draft-tab'}
              onClick={onOpenProjectDraft}
            >
              <span className="project-index">draft:</span>
              <span className="project-name">{projectDraft.name.trim() || 'Untitled project'}</span>
            </button>
          )}
          {selectedClient.projects.map((project, index) => (
            <button
              key={project.id}
              className={project.id === selectedProjectId ? 'project-tab active' : 'project-tab'}
              onClick={() => onSelectProject(project.id)}
            >
              <span className="project-index">project {index + 1}:</span>
              <span className="project-name">{project.name}</span>
            </button>
          ))}
          {!selectedClient.projects.length && <p className="rail-empty">No projects yet.</p>}
        </div>
      )}

      <div className="all-clients">
        <div className="rail-heading">All Clients</div>
        {clients.map((client) => (
          <button key={client.id} className="client-picker" onClick={() => onSelectClient(client.id)}>
            <strong>{client.fullName}</strong>
            <span>{client.projectCount} project(s)</span>
          </button>
        ))}
      </div>
    </aside>
  );
}

function RequirementPane({
  client,
  project,
  showNewClient,
  showNewProject,
  projectDraft,
  activeTab,
  onClientSaved,
  onTabChange,
  onProjectDraftChange,
  onProjectDraftDiscard,
  onProjectSaved,
  onProjectUpdated,
  onProjectDeleted,
  onNavigateToProject,
}: {
  client: ClientDetail | null;
  project: Project | null;
  showNewClient: boolean;
  showNewProject: boolean;
  projectDraft: ProjectDraft | null;
  activeTab: WorkspaceTab;
  onClientSaved: (client: ClientDetail) => Promise<void>;
  onTabChange: (tab: WorkspaceTab) => void;
  onProjectDraftChange: (draft: ProjectDraft) => void;
  onProjectDraftDiscard: () => void;
  onProjectSaved: (project: Project) => Promise<void>;
  onProjectUpdated: () => Promise<void>;
  onProjectDeleted: () => Promise<void>;
  onNavigateToProject: (clientId: number, projectId: number) => Promise<void>;
}) {
  return (
    <section className="requirement-pane">
      {showNewClient && <NewClientForm large onSaved={onClientSaved} />}
      {!showNewClient && !client && <Dashboard onNavigate={onNavigateToProject} />}
      {client && showNewProject && projectDraft && (
        <NewProjectForm
          client={client}
          value={projectDraft}
          onChange={onProjectDraftChange}
          onDiscard={onProjectDraftDiscard}
          onSaved={onProjectSaved}
        />
      )}
      {client && !showNewProject && !project && <div className="empty-workspace">Add a project under {client.fullName} to begin requirements.</div>}
      {client && !showNewProject && project && (
        <>
          <h1>Project: Requirement :-</h1>
          <div className="workspace-tabs" role="tablist" aria-label="Project workspace">
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'requirements'}
              className={activeTab === 'requirements' ? 'workspace-tab active' : 'workspace-tab'}
              onClick={() => onTabChange('requirements')}
            >
              Requirements
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'meeting_notes'}
              className={activeTab === 'meeting_notes' ? 'workspace-tab active' : 'workspace-tab'}
              onClick={() => onTabChange('meeting_notes')}
            >
              Meeting Notes
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'site_sketches'}
              className={activeTab === 'site_sketches' ? 'workspace-tab active' : 'workspace-tab'}
              onClick={() => onTabChange('site_sketches')}
            >
              Site Sketches
            </button>
          </div>
          <div className="workspace-panel">
            {activeTab === 'requirements' && (
              <>
                <ProjectRequirementForm clientId={client.id} project={project} onSaved={onProjectUpdated} onDeleted={onProjectDeleted} />
                <MilestoneTimeline project={project} />
              </>
            )}
            {activeTab === 'meeting_notes' && <MeetingNotes client={client} refresh={onProjectUpdated} />}
            {activeTab === 'site_sketches' && <SketchGallery project={project} refresh={onProjectUpdated} />}
          </div>
        </>
      )}
    </section>
  );
}

function Dashboard({ onNavigate }: { onNavigate: (clientId: number, projectId: number) => Promise<void> }) {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    getDashboardStats()
      .then((result) => {
        if (!cancelled) setStats(result);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Unable to load dashboard');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return <section className="dashboard-overview"><h1>Overview</h1><p className="rail-empty">Loading...</p></section>;
  }

  if (error) {
    return <section className="dashboard-overview"><h1>Overview</h1><p className="error">{error}</p></section>;
  }

  const dashboard = stats ?? {
    activeProjectsCount: 0,
    overdueProjectsCount: 0,
    completedThisMonthCount: 0,
    totalPendingAmountInr: 0,
    recentProjects: [],
  };

  return (
    <section className="dashboard-overview">
      <h1>Overview</h1>
      <div className="dashboard-stats">
        <div><span>Active projects</span><strong>{dashboard.activeProjectsCount}</strong></div>
        <div><span>Overdue payments</span><strong className={dashboard.overdueProjectsCount > 0 ? 'danger-value' : ''}>{dashboard.overdueProjectsCount}</strong></div>
        <div><span>Pending amount</span><strong className={dashboard.totalPendingAmountInr > 0 ? 'warning-value' : ''}>{inr(dashboard.totalPendingAmountInr)}</strong></div>
        <div><span>Completed this month</span><strong>{dashboard.completedThisMonthCount}</strong></div>
      </div>
      <div className="recent-projects">
        <h2>Recently updated</h2>
        {!dashboard.recentProjects.length && <div className="dashboard-empty">No projects yet. Add your first client to get started.</div>}
        {dashboard.recentProjects.map((item) => (
          <RecentProjectRow key={item.projectId} item={item} onNavigate={onNavigate} />
        ))}
      </div>
    </section>
  );
}

function RecentProjectRow({
  item,
  onNavigate,
}: {
  item: RecentProjectItem;
  onNavigate: (clientId: number, projectId: number) => Promise<void>;
}) {
  const [loading, setLoading] = useState(false);

  return (
    <button
      type="button"
      className="recent-project-row"
      disabled={loading}
      onClick={async () => {
        setLoading(true);
        try {
          await onNavigate(item.clientId, item.projectId);
        } finally {
          setLoading(false);
        }
      }}
    >
      <div className="recent-project-title">
        <strong>{item.clientName}</strong>
        <span>{item.projectName}</span>
      </div>
      <Badge value={item.projectStatus} />
      <Badge value={item.paymentStatus} />
      <span>{timeAgo(item.updatedAt)}</span>
    </button>
  );
}

function PaymentSidebar({
  project,
  activeTab,
  onTabChange,
  refresh,
}: {
  project: Project | null;
  activeTab: PaymentTab;
  onTabChange: (tab: PaymentTab) => void;
  refresh: () => Promise<void>;
}) {
  const [payment, setPayment] = useState({ amount: 0, paymentDate: today(), mode: 'UPI' as PaymentMode, stage: 'TOKEN' as PaymentStage, notes: '' });

  if (!project) {
    return <aside className="right-rail"><h2>Payments</h2><p className="rail-empty">Select a project to view payments.</p></aside>;
  }

  return (
    <aside className="right-rail">
      <h2>Payments</h2>
      <p className="payment-context">{project.name}</p>
      <Badge value={project.ledger.paymentStatus} />
      <div className="payment-stats">
        <div><span>Total costing</span><strong>{inr(project.ledger.totalAgreedFee)}</strong></div>
        <div><span>Paid</span><strong>{inr(project.ledger.totalPaid)}</strong></div>
        <div><span>Balance</span><strong>{inr(project.ledger.balanceDue)}</strong></div>
      </div>
      <div className="sidebar-tabs" role="tablist" aria-label="Payment workspace">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'payments'}
          className={activeTab === 'payments' ? 'sidebar-tab active' : 'sidebar-tab'}
          onClick={() => onTabChange('payments')}
        >
          Payments
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'history'}
          className={activeTab === 'history' ? 'sidebar-tab active' : 'sidebar-tab'}
          onClick={() => onTabChange('history')}
        >
          History
        </button>
      </div>
      <div className="sidebar-panel">
        {activeTab === 'payments' && (
          <form className="rail-form" onSubmit={async (event) => {
            event.preventDefault();
            await addPayment(project.id, { ...payment, amount: Number(payment.amount || 0) });
            setPayment({ amount: 0, paymentDate: today(), mode: 'UPI', stage: 'PROGRESS', notes: '' });
            await refresh();
          }}
          >
            <strong>Received Payment</strong>
            <label>Amount<input type="number" min="0" value={payment.amount} onChange={(event) => setPayment({ ...payment, amount: Number(event.target.value) })} /></label>
            <label>Date<input type="date" value={payment.paymentDate} onChange={(event) => setPayment({ ...payment, paymentDate: event.target.value })} /></label>
            <label>Mode<select value={payment.mode} onChange={(event) => setPayment({ ...payment, mode: event.target.value as PaymentMode })}>{paymentModes.map((mode) => <option key={mode} value={mode}>{label(mode)}</option>)}</select></label>
            <label>Stage<select value={payment.stage} onChange={(event) => setPayment({ ...payment, stage: event.target.value as PaymentStage })}>{paymentStages.map((stage) => <option key={stage} value={stage}>{label(stage)}</option>)}</select></label>
            <label>Note<input value={payment.notes} onChange={(event) => setPayment({ ...payment, notes: event.target.value })} /></label>
            <button className="primary">Add payment</button>
          </form>
        )}
        {activeTab === 'history' && (
          <div className="payment-list">
            {!project.ledger.entries.length && <p className="rail-empty">No payments recorded yet.</p>}
            {project.ledger.entries.map((entry) => (
              <div className="payment-item" key={entry.id}>
                <span>{entry.paymentDate}</span>
                <strong>{inr(entry.amount)}</strong>
                <span>{label(entry.stage ?? 'PROGRESS')} · {label(entry.mode)}</span>
                <button className="icon-button" onClick={async () => {
                  await deletePayment(project.id, entry.id);
                  await refresh();
                }}
                ><Trash2 size={14} /></button>
              </div>
            ))}
          </div>
        )}
      </div>
    </aside>
  );
}

function NewClientForm({ onSaved, large = false }: { onSaved: (client: ClientDetail) => Promise<void>; large?: boolean }) {
  const [form, setForm] = useState(clientDefaults());

  async function submit(event: FormEvent) {
    event.preventDefault();
    const saved = await createClient(form);
    setForm(clientDefaults());
    await onSaved(saved);
  }

  return (
    <form className={large ? 'mock-form large-form' : 'mock-form'} onSubmit={submit}>
      <strong>Add Client</strong>
      <label>Name<input required value={form.fullName} onChange={(event) => setForm({ ...form, fullName: event.target.value })} /></label>
      <label>Phone<input required value={form.phoneNumber} onChange={(event) => setForm({ ...form, phoneNumber: event.target.value })} /></label>
      <label>Email<input value={form.emailAddress} onChange={(event) => setForm({ ...form, emailAddress: event.target.value })} /></label>
      <label>Locality<input value={form.addressLocality} onChange={(event) => setForm({ ...form, addressLocality: event.target.value })} /></label>
      <label>Type<select value={form.defaultProjectType} onChange={(event) => setForm({ ...form, defaultProjectType: event.target.value as ProjectType })}>{projectTypes.map((type) => <option key={type} value={type}>{label(type)}</option>)}</select></label>
      <label>Status<select value={form.projectStatus} onChange={(event) => setForm({ ...form, projectStatus: event.target.value as ProjectStatus })}>{projectStatuses.map((status) => <option key={status} value={status}>{label(status)}</option>)}</select></label>
      <label>Plot size<input value={form.plotSize} onChange={(event) => setForm({ ...form, plotSize: event.target.value })} /></label>
      <label className="form-wide">Notes<textarea value={form.generalNotes} onChange={(event) => setForm({ ...form, generalNotes: event.target.value })} /></label>
      <button className="primary">Save client</button>
    </form>
  );
}

function NewProjectForm({
  client,
  value,
  onChange,
  onDiscard,
  onSaved,
}: {
  client: ClientDetail;
  value: ProjectDraft;
  onChange: (draft: ProjectDraft) => void;
  onDiscard: () => void;
  onSaved: (project: Project) => Promise<void>;
}) {
  async function submit(event: FormEvent) {
    event.preventDefault();
    const { clientId, ...rest } = value;
    const payload = projectPayload(rest);
    const saved = await createProject(client.id, payload);
    await onSaved(saved);
  }

  return (
    <form className="mock-form large-form" onSubmit={submit}>
      <strong>Add New Project for {client.fullName}</strong>
      <label>Name<input required value={value.name} onChange={(event) => onChange({ ...value, name: event.target.value })} /></label>
      <label>Type<select value={value.projectType} onChange={(event) => onChange({ ...value, projectType: event.target.value as ProjectType })}>{projectTypes.map((type) => <option key={type} value={type}>{label(type)}</option>)}</select></label>
      <label>Status<select value={value.status} onChange={(event) => onChange({ ...value, status: event.target.value as ProjectStatus })}>{projectStatuses.map((status) => <option key={status} value={status}>{label(status)}</option>)}</select></label>
      <label>Plot size<input value={value.plotSize} onChange={(event) => onChange({ ...value, plotSize: event.target.value })} /></label>
      <strong className="form-wide">Milestone dates</strong>
      <label>Expected completion<input type="date" value={value.expectedCompletion} onChange={(event) => onChange({ ...value, expectedCompletion: event.target.value })} /></label>
      <label className="form-wide">Requirement<textarea value={value.requirements} onChange={(event) => onChange({ ...value, requirements: event.target.value })} /></label>
      <label className="form-wide">Project notes<textarea value={value.notes} onChange={(event) => onChange({ ...value, notes: event.target.value })} /></label>
      <div className="form-actions">
        <button className="primary">Save project</button>
        <button type="button" className="danger" onClick={onDiscard}>Discard draft</button>
      </div>
    </form>
  );
}

function projectPayload(project: ProjectPayload | ProjectDraft | Project): ProjectPayload {
  const approximateBudget = 'approximateBudget' in project ? project.approximateBudget : undefined;
  return {
    name: project.name,
    projectType: project.projectType,
    status: project.status,
    plotSize: project.plotSize,
    approximateBudget,
    requirements: project.requirements,
    notes: project.notes,
    startDate: project.startDate || undefined,
    expectedCompletion: project.expectedCompletion || undefined,
    actualCompletion: project.actualCompletion || undefined,
  };
}

function ProjectRequirementForm({
  clientId,
  project,
  onSaved,
  onDeleted,
}: {
  clientId: number;
  project: Project;
  onSaved: () => Promise<void>;
  onDeleted: () => Promise<void>;
}) {
  const [form, setForm] = useState(project);
  const [totalCosting, setTotalCosting] = useState(project.ledger.totalAgreedFee);
  const sqft = plotSqft(form.plotSize);
  const estimatedCost = estimateCost(form.plotSize);

  useEffect(() => {
    setForm(project);
    setTotalCosting(project.ledger.totalAgreedFee);
  }, [project]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    await updateProject(clientId, project.id, projectPayload(form));
    await updateFee(project.id, Number(totalCosting || 0));
    await onSaved();
  }

  async function markStarted() {
    await updateProject(clientId, project.id, projectPayload({ ...project, startDate: today() }));
    await onSaved();
  }

  async function markDelivered() {
    await updateProject(clientId, project.id, projectPayload({ ...project, actualCompletion: today() }));
    await onSaved();
  }

  return (
    <form className="requirement-form" onSubmit={submit}>
      <label>Project name<input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
      <label>Type<select value={form.projectType} onChange={(event) => setForm({ ...form, projectType: event.target.value as ProjectType })}>{projectTypes.map((type) => <option key={type} value={type}>{label(type)}</option>)}</select></label>
      <label>Status<select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as ProjectStatus })}>{projectStatuses.map((status) => <option key={status} value={status}>{label(status)}</option>)}</select></label>
      <label>Plot size<input value={form.plotSize ?? ''} onChange={(event) => setForm({ ...form, plotSize: event.target.value })} /></label>
      <div className="estimate-box">
        <span className="estimate-title">Estimate cost</span>
        <span>Parsed area</span>
        <strong>{sqft === null ? '-' : `${sqft.toLocaleString('en-IN')} sq ft`}</strong>
        <span>Estimated cost @ ₹1.65/sqft</span>
        <strong>{estimatedCost === null ? '-' : inr(estimatedCost)}</strong>
        <button type="button" className="primary" disabled={estimatedCost === null} onClick={() => estimatedCost !== null && setTotalCosting(estimatedCost)}>Use estimate</button>
      </div>
      <label>Expected completion<input type="date" value={form.expectedCompletion ?? ''} onChange={(event) => setForm({ ...form, expectedCompletion: event.target.value })} /></label>
      <div className="milestone-actions">
        <button type="button" className="primary" disabled={Boolean(project.startDate)} onClick={markStarted}>Started</button>
        <button type="button" className="primary" disabled={!project.startDate || Boolean(project.actualCompletion)} onClick={markDelivered}>Delivered</button>
      </div>
      <label className="form-wide">Requirement<textarea value={form.requirements ?? ''} onChange={(event) => setForm({ ...form, requirements: event.target.value })} /></label>
      <label className="form-wide">Project notes<textarea value={form.notes ?? ''} onChange={(event) => setForm({ ...form, notes: event.target.value })} /></label>
      <label className="costing-field">Total costing after requirement<input type="number" min="0" value={totalCosting} onChange={(event) => setTotalCosting(Number(event.target.value))} /></label>
      <div className="form-actions">
        <button className="primary">Save requirement & costing</button>
        <button type="button" className="danger" onClick={async () => {
          if (!window.confirm('Permanently delete this project?')) return;
          await deleteProject(clientId, project.id);
          await onDeleted();
        }}
        >Delete project</button>
      </div>
    </form>
  );
}

function MilestoneTimeline({ project }: { project: Project }) {
  if (!project.startDate && !project.expectedCompletion && !project.actualCompletion) {
    return null;
  }

  const expectedOverdue = Boolean(
    project.expectedCompletion
    && project.expectedCompletion < today()
    && !project.actualCompletion
    && project.status !== 'COMPLETED',
  );
  const plannedDuration = project.startDate && project.expectedCompletion ? daysBetween(project.startDate, project.expectedCompletion) : null;
  const deliveredDuration = project.startDate && project.actualCompletion ? daysBetween(project.startDate, project.actualCompletion) : null;

  return (
    <section className="timeline-section">
      <h2>Project timeline</h2>
      <div className="timeline-row">
        <span className="timeline-icon" />
        <span>Start date</span>
        <strong>{formatDate(project.startDate)}</strong>
      </div>
      <div className="timeline-row">
        <span className="timeline-icon" />
        <span>Expected completion</span>
        <strong>{formatDate(project.expectedCompletion)} {expectedOverdue && <em>overdue</em>}</strong>
      </div>
      <div className="timeline-row">
        <span className="timeline-icon" />
        <span>Delivered</span>
        <strong>{formatDate(project.actualCompletion)}</strong>
      </div>
      {plannedDuration !== null && <p className="timeline-meta">Planned duration: {plannedDuration} {plannedDuration === 1 ? 'day' : 'days'}</p>}
      {deliveredDuration !== null && <p className="timeline-meta">Delivered in {deliveredDuration} {deliveredDuration === 1 ? 'day' : 'days'}</p>}
    </section>
  );
}

function MeetingNotes({ client, refresh }: { client: ClientDetail; refresh: () => Promise<void> }) {
  const [note, setNote] = useState({ noteDate: today(), content: '' });

  async function submit(event: FormEvent) {
    event.preventDefault();
    await addNote(client.id, note);
    setNote({ noteDate: today(), content: '' });
    await refresh();
  }

  return (
    <section className="meeting-section">
      <h2>Meeting Notes</h2>
      <form className="meeting-form" onSubmit={submit}>
        <input type="date" value={note.noteDate} onChange={(event) => setNote({ ...note, noteDate: event.target.value })} />
        <input required placeholder="discussion note" value={note.content} onChange={(event) => setNote({ ...note, content: event.target.value })} />
        <button className="primary">Add</button>
      </form>
      {client.meetingNotes.map((item) => (
        <div className="meeting-row" key={item.id}>
          <span>{item.noteDate}</span>
          <p>{item.content}</p>
          <button className="icon-button" onClick={async () => {
            await deleteNote(client.id, item.id);
            await refresh();
          }}
          ><Trash2 size={14} /></button>
        </div>
      ))}
    </section>
  );
}

function SketchGallery({ project, refresh }: { project: Project; refresh: () => Promise<void> }) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [caption, setCaption] = useState('');
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [activeIndex, setActiveIndex] = useState<number | null>(null);
  const sketches = project.sketches ?? [];
  const activeSketch = activeIndex === null ? null : sketches[activeIndex] ?? null;

  useEffect(() => {
    if (activeIndex === null) return undefined;
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setActiveIndex(null);
    }
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [activeIndex]);

  useEffect(() => {
    setActiveIndex(null);
    setSelectedFile(null);
    setCaption('');
    setError('');
  }, [project.id]);

  async function confirmUpload() {
    if (!selectedFile) return;
    setUploading(true);
    setError('');
    try {
      await uploadSketch(project.id, selectedFile, caption);
      setSelectedFile(null);
      setCaption('');
      if (inputRef.current) inputRef.current.value = '';
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setUploading(false);
    }
  }

  async function removeSketch(sketch: Sketch) {
    if (!window.confirm('Delete this sketch?')) return;
    try {
      await deleteSketch(project.id, sketch.id);
      const nextIndex = activeIndex === null ? null : Math.min(activeIndex, Math.max(0, sketches.length - 2));
      await refresh();
      setActiveIndex(sketches.length <= 1 ? null : nextIndex);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  }

  return (
    <section className="sketch-section">
      <div className="section-title-row">
        <h2>Site Sketches</h2>
        <button type="button" className="primary" onClick={() => inputRef.current?.click()} disabled={uploading}>
          <Upload size={14} /> Upload sketch
        </button>
      </div>
      <input
        ref={inputRef}
        className="hidden-file"
        type="file"
        accept="image/*"
        capture="environment"
        onChange={(event) => {
          setError('');
          setSelectedFile(event.target.files?.[0] ?? null);
        }}
      />
      {selectedFile && (
        <div className="sketch-upload-box">
          <span>{selectedFile.name}</span>
          <input
            maxLength={100}
            placeholder="optional caption"
            value={caption}
            onChange={(event) => setCaption(event.target.value)}
          />
          <button type="button" className="primary" onClick={confirmUpload} disabled={uploading}>
            {uploading ? 'Uploading...' : 'Confirm upload'}
          </button>
          <button type="button" className="danger" onClick={() => setSelectedFile(null)} disabled={uploading}>Cancel</button>
        </div>
      )}
      {error && <p className="error">{error}</p>}
      {!sketches.length ? (
        <div className="sketch-empty">No sketches yet. Upload the first one.</div>
      ) : (
        <div className="sketch-grid">
          {sketches.map((sketch, index) => (
            <button type="button" className="sketch-card" key={sketch.id} onClick={() => setActiveIndex(index)}>
              <img src={sketchUrl(project.id, sketch.fileName)} alt={sketch.caption || sketch.originalName} />
              {sketch.caption && <strong>{sketch.caption}</strong>}
              <span>{formatSketchDate(sketch.uploadedAt)}</span>
            </button>
          ))}
        </div>
      )}
      {activeSketch && (
        <div className="lightbox" onClick={(event) => {
          if (event.target === event.currentTarget) setActiveIndex(null);
        }}
        >
          <button type="button" className="lightbox-close" aria-label="Close" onClick={() => setActiveIndex(null)}><X size={20} /></button>
          {sketches.length > 1 && (
            <button
              type="button"
              className="lightbox-nav prev"
              aria-label="Previous sketch"
              onClick={() => setActiveIndex((index) => (index === null ? 0 : (index - 1 + sketches.length) % sketches.length))}
            >
              <ChevronLeft size={28} />
            </button>
          )}
          <div className="lightbox-content">
            <img src={sketchUrl(project.id, activeSketch.fileName)} alt={activeSketch.caption || activeSketch.originalName} />
            {activeSketch.caption && <p>{activeSketch.caption}</p>}
            <span>{formatSketchDate(activeSketch.uploadedAt)}</span>
            <button type="button" className="danger" onClick={() => removeSketch(activeSketch)}><Trash2 size={14} /> Delete sketch</button>
          </div>
          {sketches.length > 1 && (
            <button
              type="button"
              className="lightbox-nav next"
              aria-label="Next sketch"
              onClick={() => setActiveIndex((index) => (index === null ? 0 : (index + 1) % sketches.length))}
            >
              <ChevronRight size={28} />
            </button>
          )}
        </div>
      )}
    </section>
  );
}

function formatSketchDate(value: string) {
  return new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(value));
}

function formatDate(iso?: string): string {
  if (!iso) return '-';
  return new Date(iso).toLocaleDateString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

function daysBetween(startIso: string, endIso: string): number {
  const start = new Date(`${startIso}T00:00:00`);
  const end = new Date(`${endIso}T00:00:00`);
  return Math.max(0, Math.round((end.getTime() - start.getTime()) / 86400000));
}

function plotSqft(plotSize?: string): number | null {
  if (!plotSize) return null;
  const normalized = plotSize.toLowerCase().replace(/,/g, '').trim();
  const dimensionMatch = normalized.match(/(\d+(?:\.\d+)?)\s*(?:ft|feet|foot)?\s*(?:x|\*|by)\s*(\d+(?:\.\d+)?)/);
  if (dimensionMatch) {
    const width = Number(dimensionMatch[1]);
    const length = Number(dimensionMatch[2]);
    const area = width * length;
    return area > 0 ? Math.round(area) : null;
  }
  const singleMatch = normalized.match(/(\d+(?:\.\d+)?)/);
  if (!singleMatch) return null;
  const area = Number(singleMatch[1]);
  return area > 0 ? Math.round(area) : null;
}

function estimateCost(plotSize?: string): number | null {
  const area = plotSqft(plotSize);
  if (area === null) return null;
  return Math.max(1200, Math.round(area * 1.65));
}

function timeAgo(isoString: string): string {
  const diffMs = Date.now() - new Date(isoString).getTime();
  const diffMins = Math.floor(diffMs / 60000);
  if (diffMins < 60) return diffMins <= 1 ? 'just now' : `${diffMins}m ago`;
  const diffHours = Math.floor(diffMins / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  const diffDays = Math.floor(diffHours / 24);
  return diffDays === 1 ? 'yesterday' : `${diffDays} days ago`;
}

function Badge({ value }: { value: string }) {
  return <span className={`badge ${value.toLowerCase()}`}>{label(value)}</span>;
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
