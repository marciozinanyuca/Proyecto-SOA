export const BASE_URL = 'http://localhost:8080'

export type IndicatorCode =
  | 'INFLATION'
  | 'GDP'
  | 'EXCHANGE_RATE'
  | 'INTEREST_RATE'

export type InfractionSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export type InfractionStatus =
  | 'UNDER_INVESTIGATION'
  | 'CONFIRMED'
  | 'RESOLVED'
  | 'DISMISSED'

export type EsgRiskLevel = 'LOW' | 'MODERATE' | 'HIGH' | 'CRITICAL'

export interface MacroeconomicDataResponse {
  source: string
  indicatorCode: string
  indicatorName: string
  unit: string
  values: MacroeconomicObservation[]
}

export interface MacroeconomicObservation {
  date: string
  value: number
  variationPercent?: number | null
}

export interface EnvironmentalInfractionsResponse {
  source: string
  companyId: string
  total: number
  infractions: EnvironmentalInfraction[]
}

export interface EnvironmentalInfraction {
  infractionId: string
  description: string
  category?: string
  severity: InfractionSeverity
  status: InfractionStatus
  fineAmount?: number | null
  currency?: string
  reportedAt: string
}

export interface EsgRiskRequest {
  companyId: string
  period: DatePeriod
  indicatorCode: IndicatorCode
  includeResolvedInfractions?: boolean
}

export interface DatePeriod {
  startDate: string
  endDate: string
}

export interface EsgRiskResponse {
  companyId: string
  calculatedAt: string
  score: number
  level: EsgRiskLevel
  factors: EsgRiskFactors
  summary: string
}

export interface EsgRiskFactors {
  macroeconomic: MacroeconomicRiskFactor
  environmental: EnvironmentalRiskFactor
}

export interface MacroeconomicRiskFactor {
  indicatorCode: string
  indicatorValue: number
  contribution: number
}

export interface EnvironmentalRiskFactor {
  infractionCount: number
  highestSeverity?: InfractionSeverity | null
  totalFineAmount?: number
  contribution: number
}

export interface ErrorResponse {
  code: string
  message: string
  details?: string[]
}

export interface GetMacroeconomicDataParams {
  indicatorCode: IndicatorCode
  startDate: string
  endDate: string
}

export interface GetEnvironmentalInfractionsParams {
  companyId: string
  startDate?: string
  endDate?: string
}

async function fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)

  if (!response.ok) {
    let message = response.statusText || 'Error en la solicitud'

    try {
      const errorBody: unknown = await response.json()
      if (
        typeof errorBody === 'object' &&
        errorBody !== null &&
        'message' in errorBody &&
        typeof errorBody.message === 'string'
      ) {
        message = errorBody.message
      }
    } catch {
      // La respuesta de error puede no contener JSON.
    }

    throw new Error(`HTTP ${response.status}: ${message}`)
  }

  return (await response.json()) as T
}

export function getMacroeconomicData(
  params: GetMacroeconomicDataParams,
): Promise<MacroeconomicDataResponse> {
  const query = new URLSearchParams({
    indicatorCode: params.indicatorCode,
    startDate: params.startDate,
    endDate: params.endDate,
  })

  return fetchJson<MacroeconomicDataResponse>(
    `${BASE_URL}/macroeconomic-data?${query.toString()}`,
  )
}

export function getEnvironmentalInfractions(
  params: GetEnvironmentalInfractionsParams,
): Promise<EnvironmentalInfractionsResponse> {
  const query = new URLSearchParams({ companyId: params.companyId })

  if (params.startDate) query.set('startDate', params.startDate)
  if (params.endDate) query.set('endDate', params.endDate)

  return fetchJson<EnvironmentalInfractionsResponse>(
    `${BASE_URL}/environmental-infractions?${query.toString()}`,
  )
}

export function calculateEsgRisk(
  request: EsgRiskRequest,
): Promise<EsgRiskResponse> {
  return fetchJson<EsgRiskResponse>(`${BASE_URL}/esg-risk`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}