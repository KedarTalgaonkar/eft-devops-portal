/** Mirrors the backend PasswordPolicy so users see the rules while typing (the backend still enforces them). */
export interface PolicyCheck {
  label: string;
  ok: boolean;
}

export function passwordChecks(password: string, username: string): PolicyCheck[] {
  const classes = [/[a-z]/, /[A-Z]/, /\d/, /[^A-Za-z0-9]/].filter((r) => r.test(password)).length;
  const lower = password.toLowerCase();
  return [
    { label: 'At least 12 characters', ok: password.length >= 12 && password.length <= 128 },
    { label: 'Three of: lower case, upper case, digit, symbol', ok: classes >= 3 },
    { label: 'Does not contain your username', ok: !username || username.length < 3 || !lower.includes(username.toLowerCase()) },
    { label: 'No character repeated 4 times in a row', ok: !/(.)\1{3,}/.test(password) },
  ];
}
