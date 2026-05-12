import type { ClientDetail, ClientSummary, Ledger, PaymentMode, PaymentStage, Project, ProjectStatus, ProjectType, Sketch } from '../types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

function token() {
  return localStorage.getItem('archdesk.token');
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Content-Type', 'application/json');
  const authToken = token();
  if (authToken) headers.set('Authorization', `Bearer ${authToken}`);

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      logout();
      window.dispatchEvent(new Event('archdesk.auth.expired'));
    }
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      message = body.message ?? message;
    } catch {
      // keep default message
    }
    throw new Error(message);
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return text ? JSON.parse(text) as T : undefined as T;
}

export async function login(email: string, password: string) {
  const result = await request<{ token: string; email: string }>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
  localStorage.setItem('archdesk.token', result.token);
  localStorage.setItem('archdesk.email', result.email);
  return result;
}

export function logout() {
  localStorage.removeItem('archdesk.token');
  localStorage.removeItem('archdesk.email');
}

export function isLoggedIn() {
  return Boolean(token());
}

export interface ClientPayload {
  fullName: string;
  phoneNumber: string;
  emailAddress?: string;
  addressLocality?: string;
  defaultProjectType: ProjectType;
  plotSize?: string;
  approximateBudget?: number;
  projectStatus: ProjectStatus;
  generalNotes?: string;
}

export interface ProjectPayload {
  name: string;
  projectType: ProjectType;
  status: ProjectStatus;
  plotSize?: string;
  approximateBudget?: number;
  requirements?: string;
  notes?: string;
}

export function listClients(params: URLSearchParams) {
  const qs = params.toString();
  return request<ClientSummary[]>(`/api/clients${qs ? `?${qs}` : ''}`);
}

export function getClient(id: number) {
  return request<ClientDetail>(`/api/clients/${id}`);
}

export function createClient(payload: ClientPayload) {
  return request<ClientDetail>('/api/clients', { method: 'POST', body: JSON.stringify(payload) });
}

export function updateClient(id: number, payload: ClientPayload) {
  return request<ClientDetail>(`/api/clients/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
}

export function deleteClient(id: number) {
  return request<void>(`/api/clients/${id}`, { method: 'DELETE' });
}

export function createProject(clientId: number, payload: ProjectPayload) {
  return request<Project>(`/api/clients/${clientId}/projects`, { method: 'POST', body: JSON.stringify(payload) });
}

export function updateProject(clientId: number, projectId: number, payload: ProjectPayload) {
  return request<Project>(`/api/clients/${clientId}/projects/${projectId}`, { method: 'PUT', body: JSON.stringify(payload) });
}

export function deleteProject(clientId: number, projectId: number) {
  return request<void>(`/api/clients/${clientId}/projects/${projectId}`, { method: 'DELETE' });
}

export function addNote(clientId: number, payload: { noteDate: string; content: string }) {
  return request(`/api/clients/${clientId}/notes`, { method: 'POST', body: JSON.stringify(payload) });
}

export function updateNote(clientId: number, noteId: number, payload: { noteDate: string; content: string }) {
  return request(`/api/clients/${clientId}/notes/${noteId}`, { method: 'PUT', body: JSON.stringify(payload) });
}

export function deleteNote(clientId: number, noteId: number) {
  return request<void>(`/api/clients/${clientId}/notes/${noteId}`, { method: 'DELETE' });
}

export function updateFee(projectId: number, totalAgreedFee: number) {
  return request<Ledger>(`/api/projects/${projectId}/payments/fee`, {
    method: 'PUT',
    body: JSON.stringify({ totalAgreedFee }),
  });
}

export function addPayment(projectId: number, payload: { amount: number; paymentDate: string; mode: PaymentMode; stage: PaymentStage; notes?: string }) {
  return request<Ledger>(`/api/projects/${projectId}/payments/entries`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updatePayment(projectId: number, entryId: number, payload: { amount: number; paymentDate: string; mode: PaymentMode; stage: PaymentStage; notes?: string }) {
  return request<Ledger>(`/api/projects/${projectId}/payments/entries/${entryId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function deletePayment(projectId: number, entryId: number) {
  return request<Ledger>(`/api/projects/${projectId}/payments/entries/${entryId}`, { method: 'DELETE' });
}

export async function uploadSketch(projectId: number, file: File, caption?: string): Promise<Sketch> {
  const form = new FormData();
  form.append('file', file);
  if (caption?.trim()) form.append('caption', caption.trim());

  const headers = new Headers();
  const authToken = token();
  if (authToken) headers.set('Authorization', `Bearer ${authToken}`);

  const response = await fetch(`${API_BASE}/api/projects/${projectId}/sketches`, {
    method: 'POST',
    headers,
    body: form,
  });
  return handleResponse<Sketch>(response);
}

export function updateSketchCaption(projectId: number, sketchId: number, caption: string): Promise<Sketch> {
  return request<Sketch>(`/api/projects/${projectId}/sketches/${sketchId}`, {
    method: 'PATCH',
    body: JSON.stringify({ caption }),
  });
}

export function deleteSketch(projectId: number, sketchId: number): Promise<void> {
  return request<void>(`/api/projects/${projectId}/sketches/${sketchId}`, { method: 'DELETE' });
}

export function sketchUrl(projectId: number, fileName: string): string {
  return `${API_BASE}/uploads/sketches/${projectId}/${fileName}`;
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      logout();
      window.dispatchEvent(new Event('archdesk.auth.expired'));
    }
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      message = body.message ?? message;
    } catch {
      // keep default message
    }
    throw new Error(message);
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return text ? JSON.parse(text) as T : undefined as T;
}
