import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

/**
 * Lightweight i18n for the console — no heavy dependency. Five European locales (covering most of
 * the EU); missing keys fall back to English. Locale also drives `Intl` date / number / currency
 * formatting, so the whole UI reads natively for a European audience.
 */
export type Locale = 'en' | 'de' | 'fr' | 'es' | 'it'

export const LOCALES: { code: Locale; label: string; flag: string; intl: string }[] = [
  { code: 'en', label: 'English', flag: '🇬🇧', intl: 'en-GB' },
  { code: 'de', label: 'Deutsch', flag: '🇩🇪', intl: 'de-DE' },
  { code: 'fr', label: 'Français', flag: '🇫🇷', intl: 'fr-FR' },
  { code: 'es', label: 'Español', flag: '🇪🇸', intl: 'es-ES' },
  { code: 'it', label: 'Italiano', flag: '🇮🇹', intl: 'it-IT' },
]

// Keys are English; non-English dicts override what they translate, else fall back to the key text.
const EN = {
  'nav.console': 'Console',
  'nav.govern': 'Govern',
  'nav.operate': 'Operate',
  'nav.dashboard': 'Dashboard',
  'nav.audit': 'Audit log',
  'nav.users': 'Users',
  'nav.policies': 'Policies',
  'nav.models': 'Models & routing',
  'nav.usage': 'Usage & cost',
  'nav.keys': 'API keys',
  'nav.team': 'Team & roles',
  'nav.settings': 'Settings',
  'common.search': 'Search',
  'common.save': 'Save',
  'common.cancel': 'Cancel',
  'common.create': 'Create',
  'common.delete': 'Delete',
  'common.edit': 'Edit',
  'common.retry': 'Retry',
  'common.loading': 'Loading…',
  'common.prev': 'Previous',
  'common.next': 'Next',
  'common.signOut': 'Sign out',
  'common.dark': 'Dark',
  'common.light': 'Light',
  'common.allVerdicts': 'All verdicts',
  'dash.overview': 'Overview',
  'dash.subtitle': 'Live traffic across the gateway',
  'dash.healthy': 'Gateway healthy',
  'dash.totalRequests': 'Total requests',
  'dash.piiRedacted': 'PII redacted',
  'dash.blocked': 'Blocked',
  'dash.totalCost': 'Total cost',
  'dash.recent': 'Recent requests',
  'dash.viewAll': 'View all',
  'dash.leaks': 'Leaks prevented',
  'dash.topModels': 'Top models',
  'audit.title': 'Audit log',
  'audit.subtitle': 'Every request, kept forever, tamper-proof.',
  'audit.chainVerified': 'Chain verified',
  'audit.searchPlaceholder': 'Search prompts, users, models…',
  'audit.time': 'Time',
  'audit.request': 'Request',
  'audit.model': 'Model',
  'audit.verdict': 'Verdict',
  'verdict.allowed': 'allowed',
  'verdict.redacted': 'redacted',
  'verdict.blocked': 'blocked',
}

type Key = keyof typeof EN

const DE: Partial<Record<Key, string>> = {
  'nav.console': 'Konsole',
  'nav.govern': 'Steuern',
  'nav.operate': 'Betrieb',
  'nav.dashboard': 'Übersicht',
  'nav.audit': 'Audit-Protokoll',
  'nav.users': 'Benutzer',
  'nav.policies': 'Richtlinien',
  'nav.models': 'Modelle & Routing',
  'nav.usage': 'Nutzung & Kosten',
  'nav.keys': 'API-Schlüssel',
  'nav.team': 'Team & Rollen',
  'nav.settings': 'Einstellungen',
  'common.search': 'Suchen',
  'common.save': 'Speichern',
  'common.cancel': 'Abbrechen',
  'common.retry': 'Erneut',
  'common.prev': 'Zurück',
  'common.next': 'Weiter',
  'common.signOut': 'Abmelden',
  'common.dark': 'Dunkel',
  'common.light': 'Hell',
  'common.allVerdicts': 'Alle Urteile',
  'dash.overview': 'Übersicht',
  'dash.subtitle': 'Live-Verkehr über das Gateway',
  'dash.healthy': 'Gateway funktioniert',
  'dash.totalRequests': 'Anfragen gesamt',
  'dash.piiRedacted': 'PII geschwärzt',
  'dash.blocked': 'Blockiert',
  'dash.totalCost': 'Gesamtkosten',
  'dash.recent': 'Letzte Anfragen',
  'dash.viewAll': 'Alle ansehen',
  'dash.leaks': 'Verhinderte Lecks',
  'dash.topModels': 'Top-Modelle',
  'audit.title': 'Audit-Protokoll',
  'audit.subtitle': 'Jede Anfrage, dauerhaft gespeichert, fälschungssicher.',
  'audit.chainVerified': 'Kette verifiziert',
}

const FR: Partial<Record<Key, string>> = {
  'nav.console': 'Console',
  'nav.govern': 'Gouverner',
  'nav.operate': 'Exploiter',
  'nav.dashboard': 'Tableau de bord',
  'nav.audit': "Journal d'audit",
  'nav.users': 'Utilisateurs',
  'nav.policies': 'Politiques',
  'nav.models': 'Modèles & routage',
  'nav.usage': 'Usage & coûts',
  'nav.keys': 'Clés API',
  'nav.team': 'Équipe & rôles',
  'nav.settings': 'Paramètres',
  'common.search': 'Rechercher',
  'common.save': 'Enregistrer',
  'common.cancel': 'Annuler',
  'common.prev': 'Précédent',
  'common.next': 'Suivant',
  'common.signOut': 'Se déconnecter',
  'common.dark': 'Sombre',
  'common.light': 'Clair',
  'dash.overview': "Vue d'ensemble",
  'dash.subtitle': 'Trafic en direct sur la passerelle',
  'dash.healthy': 'Passerelle opérationnelle',
  'dash.totalRequests': 'Requêtes totales',
  'dash.piiRedacted': 'PII masquées',
  'dash.blocked': 'Bloquées',
  'dash.totalCost': 'Coût total',
  'dash.recent': 'Requêtes récentes',
  'dash.leaks': 'Fuites évitées',
  'dash.topModels': 'Meilleurs modèles',
  'audit.title': "Journal d'audit",
  'audit.chainVerified': 'Chaîne vérifiée',
}

const ES: Partial<Record<Key, string>> = {
  'nav.dashboard': 'Panel',
  'nav.audit': 'Registro de auditoría',
  'nav.policies': 'Políticas',
  'nav.usage': 'Uso y costes',
  'nav.settings': 'Ajustes',
  'common.search': 'Buscar',
  'common.signOut': 'Cerrar sesión',
  'dash.overview': 'Resumen',
  'dash.subtitle': 'Tráfico en vivo a través de la pasarela',
  'dash.totalRequests': 'Solicitudes totales',
  'dash.blocked': 'Bloqueadas',
  'dash.totalCost': 'Coste total',
  'audit.chainVerified': 'Cadena verificada',
}

const IT: Partial<Record<Key, string>> = {
  'nav.dashboard': 'Pannello',
  'nav.audit': 'Registro di controllo',
  'nav.policies': 'Criteri',
  'nav.usage': 'Utilizzo e costi',
  'nav.settings': 'Impostazioni',
  'common.search': 'Cerca',
  'common.signOut': 'Esci',
  'dash.overview': 'Panoramica',
  'dash.subtitle': 'Traffico in tempo reale attraverso il gateway',
  'dash.totalRequests': 'Richieste totali',
  'dash.blocked': 'Bloccate',
  'dash.totalCost': 'Costo totale',
  'audit.chainVerified': 'Catena verificata',
}

const DICTS: Record<Locale, Partial<Record<Key, string>>> = { en: EN, de: DE, fr: FR, es: ES, it: IT }

// Module-level active Intl locale, so plain formatters (money/dt) read the current language.
let activeIntl = 'en-GB'
export function intlLocale(): string {
  return activeIntl
}

interface I18n {
  locale: Locale
  setLocale: (l: Locale) => void
  t: (key: Key, vars?: Record<string, string | number>) => string
}

const Ctx = createContext<I18n | null>(null)

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(
    () => (localStorage.getItem('auvex.locale') as Locale) || 'en',
  )
  activeIntl = LOCALES.find((l) => l.code === locale)?.intl ?? 'en-GB'

  useEffect(() => {
    document.documentElement.lang = locale
  }, [locale])

  const value = useMemo<I18n>(() => {
    const dict = DICTS[locale]
    return {
      locale,
      setLocale: (l) => {
        localStorage.setItem('auvex.locale', l)
        activeIntl = LOCALES.find((x) => x.code === l)?.intl ?? 'en-GB'
        setLocaleState(l)
      },
      t: (key, vars) => {
        let s = dict[key] ?? EN[key] ?? String(key)
        if (vars) for (const k of Object.keys(vars)) s = s.replace(`{${k}}`, String(vars[k]))
        return s
      },
    }
  }, [locale])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useT() {
  const ctx = useContext(Ctx)
  if (!ctx) throw new Error('useT must be used within I18nProvider')
  return ctx
}
