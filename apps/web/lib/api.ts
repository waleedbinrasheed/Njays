const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export type User = {
  id: number;
  email: string;
  fullName: string;
  phone?: string;
  role: string;
};

export type Product = {
  id: number;
  name: string;
  slug: string;
  description: string;
  basePricePaisa: number;
  currency: string;
  supportsCustom: boolean;
  categoryId: number;
  images: { url: string; altText?: string; sortOrder: number }[];
};

export type FabricTier = {
  id: number;
  code: string;
  name: string;
  surchargePaisa: number;
  colors: { id: number; code: string; name: string; hexColor?: string }[];
};

export type Branch = {
  id: number;
  name: string;
  address?: string;
  phone?: string;
  active: boolean;
};

export type DispatchCostRule = {
  id: number;
  sourceBranchId: number;
  sourceBranchName: string;
  destinationBranchId: number;
  destinationBranchName: string;
  costPaisa: number;
};

export type DispatchCostSettings = {
  defaultCostPaisa: number;
  rules: DispatchCostRule[];
};

export type Category = { id: number; name: string; slug: string; description?: string };

export type OrderItem = {
  productId: number;
  productName: string;
  quantity: number;
  custom: boolean;
  fabricLabel?: string;
  unitPricePaisa: number;
  lineTotalPaisa: number;
};

export type StatusHistoryEntry = { fromStatus?: string; toStatus: string; note?: string; createdAt: string };

export type Order = {
  id: number;
  publicCode: string;
  orderType: string;
  status: string;
  currency: string;
  subtotalPaisa: number;
  discountPaisa: number;
  dispatchCostPaisa: number;
  totalPaisa: number;
  amountPaidPaisa: number;
  balanceDuePaisa: number;
  whatsappPhone?: string;
  customerNote?: string;
  createdBranchId?: number;
  createdBranchName?: string;
  dispatchBranchId?: number;
  dispatchBranchName?: string;
  expectedDeliveryDate?: string;
  items: OrderItem[];
  timeline: StatusHistoryEntry[];
  createdAt: string;
};

export type CustomerDetail = {
  id: number;
  fullName: string;
  phone?: string;
  email?: string;
  measurements: Measurement[];
  orders: Order[];
  outstandingBalancePaisa: number;
};

export type DashboardSummary = {
  todaysOrders: number;
  ordersInProgress: number;
  readyForDelivery: number;
  ordersDueToday: number;
  overdueOrders: number;
  todaysPaymentsPaisa: number;
  outstandingPayments: number;
  awaitingDispatch: number;
};

export type Measurement = {
  id: number;
  name: string;
  unit: string;
  kameezLength?: number;
  chest?: number;
  waist?: number;
  hip?: number;
  shoulder?: number;
  sleeveLength?: number;
  collarLength?: number;
  shalwarLength?: number;
  shalwarBottom?: number;
  backStyle?: string;
  sleeveStyle?: string;
  buttonStyle?: string;
  collarStyle?: string;
  cuffStyle?: string;
  notes?: string;
  isDefault: boolean;
};

function authHeaders(): HeadersInit {
  if (typeof window === "undefined") return {};
  const token = localStorage.getItem("accessToken");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function readError(res: Response): Promise<string> {
  try {
    const body = await res.json();
    if (body.error) return String(body.error);
    if (body.fields) return JSON.stringify(body.fields);
    return JSON.stringify(body);
  } catch {
    return res.statusText || `HTTP ${res.status}`;
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...authHeaders(),
      ...(init.headers || {}),
    },
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(await readError(res));
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

/** Multipart/form-data helper — do not set Content-Type (browser sets boundary). */
export async function apiForm<T>(path: string, formData: FormData): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    method: "POST",
    headers: {
      ...authHeaders(),
    },
    body: formData,
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(await readError(res));
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

export function formatPkr(paisa: number) {
  return new Intl.NumberFormat("en-PK", {
    style: "currency",
    currency: "PKR",
    maximumFractionDigits: 0,
  }).format(paisa / 100);
}

export { API_URL };
