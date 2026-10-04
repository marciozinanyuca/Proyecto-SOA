import { useState } from 'react'
import {
  calculateEsgRisk,
  getEnvironmentalInfractions,
  getMacroeconomicData,
} from './services/api'
import type {
  EnvironmentalInfractionsResponse,
  EsgRiskResponse,
  IndicatorCode,
  MacroeconomicDataResponse,
} from './services/api'
import './App.css'

const indicatorOptions: { value: IndicatorCode; label: string }[] = [
  { value: 'INFLATION', label: 'Inflación' },
  { value: 'GDP', label: 'PBI' },
  { value: 'EXCHANGE_RATE', label: 'Tipo de cambio' },
  { value: 'INTEREST_RATE', label: 'Tasa de interés' },
]

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Ocurrió un error inesperado.'
}

function formatDate(date: string): string {
  return new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium' }).format(
    new Date(`${date.slice(0, 10)}T00:00:00`),
  )
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat('es-PE', { maximumFractionDigits: 2 }).format(value)
}

function App() {
  const [indicatorCode, setIndicatorCode] = useState<IndicatorCode>('INFLATION')
  const [companyId, setCompanyId] = useState('20123456789')
  const [startDate, setStartDate] = useState('2025-01-01')
  const [endDate, setEndDate] = useState('2025-12-31')
  const [includeResolved, setIncludeResolved] = useState(false)

  const [macroData, setMacroData] = useState<MacroeconomicDataResponse | null>(null)
  const [infractionsData, setInfractionsData] =
    useState<EnvironmentalInfractionsResponse | null>(null)
  const [riskData, setRiskData] = useState<EsgRiskResponse | null>(null)

  const [macroLoading, setMacroLoading] = useState(false)
  const [infractionsLoading, setInfractionsLoading] = useState(false)
  const [riskLoading, setRiskLoading] = useState(false)
  const [macroError, setMacroError] = useState('')
  const [infractionsError, setInfractionsError] = useState('')
  const [riskError, setRiskError] = useState('')

  async function submitMacroeconomic(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setMacroLoading(true)
    setMacroError('')
    try {
      setMacroData(await getMacroeconomicData({ indicatorCode, startDate, endDate }))
    } catch (error) {
      setMacroError(errorMessage(error))
    } finally {
      setMacroLoading(false)
    }
  }

  async function submitInfractions(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setInfractionsLoading(true)
    setInfractionsError('')
    try {
      setInfractionsData(
        await getEnvironmentalInfractions({ companyId, startDate, endDate }),
      )
    } catch (error) {
      setInfractionsError(errorMessage(error))
    } finally {
      setInfractionsLoading(false)
    }
  }

  async function submitRisk(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setRiskLoading(true)
    setRiskError('')
    try {
      setRiskData(
        await calculateEsgRisk({
          companyId,
          period: { startDate, endDate },
          indicatorCode,
          includeResolvedInfractions: includeResolved,
        }),
      )
    } catch (error) {
      setRiskError(errorMessage(error))
    } finally {
      setRiskLoading(false)
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#inicio" aria-label="EcoMetrics, inicio">
          <span className="brand-mark" aria-hidden="true">E</span>
          <span>EcoMetrics</span>
        </a>
        <div className="api-indicator"><span /> API local · localhost:8080</div>
      </header>

      <main id="inicio">
        <section className="intro" aria-labelledby="page-title">
          <div>
            <p className="eyebrow">INTELIGENCIA PARA DECISIONES SOSTENIBLES</p>
            <h1 id="page-title">Panorama de riesgo <em>corporativo</em></h1>
            <p className="intro-copy">
              Consulta indicadores económicos, revisa el historial ambiental y calcula
              el perfil ESG de una empresa.
            </p>
          </div>
          <div className="intro-stamp" aria-hidden="true">
            <span>ESG</span>
            <strong>PE</strong>
            <small>DATA / 01</small>
          </div>
        </section>

        <section className="filters" aria-label="Parámetros de consulta">
          <div className="filter-heading">
            <span className="section-index">00</span>
            <h2>Parámetros del análisis</h2>
          </div>
          <div className="filter-fields">
            <label className="field company-field">
              <span>RUC de empresa</span>
              <input
                type="text"
                inputMode="numeric"
                pattern="[0-9]{11}"
                maxLength={11}
                value={companyId}
                onChange={(event) => setCompanyId(event.target.value)}
                required
                aria-describedby="ruc-hint"
              />
              <small id="ruc-hint">11 dígitos</small>
            </label>
            <label className="field">
              <span>Desde</span>
              <input
                type="date"
                value={startDate}
                max={endDate}
                onChange={(event) => setStartDate(event.target.value)}
                required
              />
            </label>
            <label className="field">
              <span>Hasta</span>
              <input
                type="date"
                value={endDate}
                min={startDate}
                onChange={(event) => setEndDate(event.target.value)}
                required
              />
            </label>
            <label className="field indicator-field">
              <span>Indicador macroeconómico</span>
              <select
                value={indicatorCode}
                onChange={(event) => setIndicatorCode(event.target.value as IndicatorCode)}
              >
                {indicatorOptions.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>
          </div>
        </section>

        <section className="workspace" aria-label="Consultas de datos">
          <article className="data-panel macro-panel">
            <div className="panel-heading">
              <span className="section-index">01</span>
              <div>
                <p className="panel-kicker">BCRP · ECONOMÍA</p>
                <h2>Indicadores macroeconómicos</h2>
              </div>
            </div>
            <p className="panel-description">Serie histórica del indicador seleccionado para el periodo indicado.</p>
            <form onSubmit={submitMacroeconomic}>
              <button className="action-button" type="submit" disabled={macroLoading}>
                {macroLoading ? 'Consultando…' : 'Consultar indicador'}
                <span aria-hidden="true">↗</span>
              </button>
            </form>
            <div className="result-area" aria-live="polite">
              {macroError && <p className="error-message" role="alert">{macroError}</p>}
              {macroData ? (
                <>
                  <div className="result-meta">
                    <span>{macroData.source}</span>
                    <strong>{macroData.indicatorName}</strong>
                    <small>Unidad: {macroData.unit}</small>
                  </div>
                  {macroData.values.length ? (
                    <div className="table-scroll">
                      <table>
                        <thead><tr><th>Fecha</th><th>Valor</th><th>Variación</th></tr></thead>
                        <tbody>
                          {macroData.values.map((observation) => (
                            <tr key={`${observation.date}-${observation.value}`}>
                              <td>{formatDate(observation.date)}</td>
                              <td className="numeric-cell">{formatNumber(observation.value)}</td>
                              <td className="numeric-cell">
                                {observation.variationPercent == null
                                  ? '—'
                                  : `${formatNumber(observation.variationPercent)}%`}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : <p className="empty-result">No hay valores para este periodo.</p>}
                </>
              ) : !macroError && (
                <p className="empty-prompt">La serie consultada aparecerá aquí.</p>
              )}
            </div>
          </article>

          <article className="data-panel infraction-panel">
            <div className="panel-heading">
              <span className="section-index">02</span>
              <div>
                <p className="panel-kicker">OEFA · AMBIENTE</p>
                <h2>Infracciones ambientales</h2>
              </div>
            </div>
            <p className="panel-description">Sanciones y casos reportados asociados al RUC de la empresa.</p>
            <form onSubmit={submitInfractions}>
              <button className="action-button" type="submit" disabled={infractionsLoading}>
                {infractionsLoading ? 'Consultando…' : 'Consultar infracciones'}
                <span aria-hidden="true">↗</span>
              </button>
            </form>
            <div className="result-area" aria-live="polite">
              {infractionsError && <p className="error-message" role="alert">{infractionsError}</p>}
              {infractionsData ? (
                <>
                  <div className="total-line">
                    <strong>{infractionsData.total}</strong>
                    <span>infracciones registradas</span>
                    <small>{infractionsData.source}</small>
                  </div>
                  {infractionsData.infractions.length ? (
                    <ul className="infraction-list">
                      {infractionsData.infractions.map((infraction) => (
                        <li key={infraction.infractionId}>
                          <div className="infraction-title">
                            <strong>{infraction.description}</strong>
                            <span className={`severity severity-${infraction.severity.toLowerCase()}`}>
                              {infraction.severity}
                            </span>
                          </div>
                          <p>
                            {infraction.category ?? 'Sin categoría'} · {infraction.status.replaceAll('_', ' ')}
                          </p>
                          <small>{formatDate(infraction.reportedAt)} · {infraction.infractionId}</small>
                        </li>
                      ))}
                    </ul>
                  ) : <p className="empty-result">No hay infracciones para estos criterios.</p>}
                </>
              ) : !infractionsError && (
                <p className="empty-prompt">Los registros ambientales aparecerán aquí.</p>
              )}
            </div>
          </article>

          <article className="data-panel risk-panel">
            <div className="panel-heading">
              <span className="section-index">03</span>
              <div>
                <p className="panel-kicker">ECOMETRICS · EVALUACIÓN</p>
                <h2>Riesgo ESG</h2>
              </div>
            </div>
            <p className="panel-description">Combina el indicador económico y el historial ambiental en una puntuación.</p>
            <form onSubmit={submitRisk}>
              <label className="resolved-toggle">
                <input
                  type="checkbox"
                  checked={includeResolved}
                  onChange={(event) => setIncludeResolved(event.target.checked)}
                />
                <span>Incluir infracciones resueltas</span>
              </label>
              <button className="action-button risk-action" type="submit" disabled={riskLoading}>
                {riskLoading ? 'Calculando…' : 'Calcular riesgo ESG'}
                <span aria-hidden="true">↗</span>
              </button>
            </form>
            <div className="result-area" aria-live="polite">
              {riskError && <p className="error-message" role="alert">{riskError}</p>}
              {riskData ? (
                <>
                  <div className="risk-score">
                    <div className="score-number">
                      <strong>{formatNumber(riskData.score)}</strong>
                      <span>/ 100</span>
                    </div>
                    <span className={`risk-level level-${riskData.level.toLowerCase()}`}>
                      {riskData.level}
                    </span>
                  </div>
                  <div
                    className="score-meter"
                    role="meter"
                    aria-label="Puntuación de riesgo ESG"
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-valuenow={riskData.score}
                  >
                    <span style={{ width: `${Math.min(100, Math.max(0, riskData.score))}%` }} />
                  </div>
                  <p className="risk-summary">{riskData.summary}</p>
                  <div className="factor-grid">
                    <div>
                      <span>Factor macroeconómico</span>
                      <strong>{formatNumber(riskData.factors.macroeconomic.contribution)}%</strong>
                      <small>{riskData.factors.macroeconomic.indicatorCode}</small>
                    </div>
                    <div>
                      <span>Factor ambiental</span>
                      <strong>{formatNumber(riskData.factors.environmental.contribution)}%</strong>
                      <small>{riskData.factors.environmental.infractionCount} infracciones</small>
                    </div>
                  </div>
                </>
              ) : !riskError && (
                <p className="empty-prompt">El resultado del análisis aparecerá aquí.</p>
              )}
            </div>
          </article>
        </section>
      </main>

      <footer className="page-footer">
        <span>EcoMetrics</span>
        <span>Datos simulados para evaluación ESG</span>
      </footer>
    </div>
  )
}

export default App
