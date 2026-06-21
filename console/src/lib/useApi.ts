import { useCallback, useEffect, useState } from 'react'

/** Runs an async API call, exposing { data, loading, error, reload }. */
export function useApi<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(fnLoader, deps)

  function fnLoader() {
    setLoading(true)
    setError(undefined)
    return fn()
      .then((d) => setData(d))
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Something went wrong'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    void load()
  }, [load])

  return { data, loading, error, reload: load }
}
