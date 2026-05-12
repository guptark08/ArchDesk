export type ProjectType = 'RESIDENTIAL' | 'COMMERCIAL' | 'RENOVATION';
export type ProjectStatus = 'ACTIVE' | 'ON_HOLD' | 'COMPLETED';
export type PaymentStatus = 'FULLY_PAID' | 'PARTIAL' | 'PENDING' | 'OVERDUE';
export type PaymentMode = 'CASH' | 'BANK_TRANSFER' | 'UPI' | 'CHEQUE';
export type PaymentStage = 'TOKEN' | 'PROGRESS' | 'FINAL';

export interface ClientSummary {
  id: number;
  fullName: string;
  phoneNumber: string;
  addressLocality?: string;
  defaultProjectType: ProjectType;
  projectStatus: ProjectStatus;
  paymentStatus: PaymentStatus;
  projectCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ClientDetail extends ClientSummary {
  emailAddress?: string;
  plotSize?: string;
  approximateBudget?: number;
  generalNotes?: string;
  projects: Project[];
  meetingNotes: MeetingNote[];
}

export interface Project {
  id: number;
  name: string;
  projectType: ProjectType;
  status: ProjectStatus;
  plotSize?: string;
  approximateBudget?: number;
  requirements?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
  ledger: Ledger;
  sketches: Sketch[];
}

export interface Sketch {
  id: number;
  fileName: string;
  originalName: string;
  caption?: string;
  fileSize: number;
  mimeType: string;
  uploadedAt: string;
}

export interface Ledger {
  id: number;
  totalAgreedFee: number;
  totalPaid: number;
  balanceDue: number;
  paymentStatus: PaymentStatus;
  entries: PaymentEntry[];
}

export interface PaymentEntry {
  id: number;
  amount: number;
  paymentDate: string;
  mode: PaymentMode;
  stage: PaymentStage;
  notes?: string;
}

export interface MeetingNote {
  id: number;
  noteDate: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}
