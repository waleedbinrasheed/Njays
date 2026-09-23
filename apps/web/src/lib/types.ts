export type Role = "ADMIN" | "USER";

export interface MeResponse {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  branchId: number | null;
}

export interface BranchResponse {
  id: number;
  name: string;
  address: string | null;
  phone: string | null;
  active: boolean;
}

export interface UserSummary {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  branchId: number | null;
  enabled: boolean;
}
