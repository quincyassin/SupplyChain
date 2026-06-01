import axios, { AxiosError } from "axios";

export interface LicenseStatus {
  licensed: boolean;
  enforced: boolean;
  machineId: string;
  machineIdDisplay: string;
  platform: string;
  expireAt?: string;
  remainingDays?: number;
  message?: string;
}

interface ApiErrorBody {
  success?: boolean;
  message?: string;
  code?: string;
}

const client = axios.create({
  baseURL: "/api/license",
  timeout: 30000,
});

async function extractErrorMessage(error: unknown): Promise<string> {
  if (!axios.isAxiosError(error)) {
    return error instanceof Error ? error.message : "请求失败";
  }
  const axiosError = error as AxiosError<ApiErrorBody>;
  if (!axiosError.response) {
    return "无法连接后端，请确认服务已启动（端口 8080）";
  }
  return axiosError.response.data?.message ?? "请求失败";
}

export async function fetchLicenseStatus(): Promise<LicenseStatus> {
  try {
    const response = await client.get<LicenseStatus>("/status");
    return response.data;
  } catch (error) {
    throw new Error(await extractErrorMessage(error));
  }
}

export async function activateLicense(activationCode: string): Promise<LicenseStatus> {
  try {
    const response = await client.post<LicenseStatus>("/activate", { activationCode });
    return response.data;
  } catch (error) {
    throw new Error(await extractErrorMessage(error));
  }
}
