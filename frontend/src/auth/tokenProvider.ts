/**
 * In-memory access-token source. The Auth0 React integration sets a provider
 * backed by {@code getAccessTokenSilently()} (token held in memory by the SDK).
 * Tokens are never read from localStorage/sessionStorage — Module Federation's
 * shared origin would make browser-stored tokens an XSS exfiltration target.
 */
export type TokenProvider = () => Promise<string | null>;

let provider: TokenProvider = async () => null;

export function setTokenProvider(next: TokenProvider): void {
  provider = next;
}

export function getAccessToken(): Promise<string | null> {
  return provider();
}
