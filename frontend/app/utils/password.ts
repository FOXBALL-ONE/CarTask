export const MIN_PASSWORD_LENGTH = 8;
export const MIN_PASSWORD_CHARACTER_CLASSES = 3;

export function passwordPolicyError(password: string, label = "密码"): string | null {
  if (password.length < MIN_PASSWORD_LENGTH) return `${label}至少需要 ${MIN_PASSWORD_LENGTH} 位字符`;
  if (/\s/u.test(password)) return `${label}不能包含空白字符`;

  const characterClasses = [
    /\p{Ll}/u.test(password),
    /\p{Lu}/u.test(password),
    /\p{Nd}/u.test(password),
    /[^\p{L}\p{Nd}\s]/u.test(password),
  ].filter(Boolean).length;
  if (characterClasses < MIN_PASSWORD_CHARACTER_CLASSES) {
    return `${label}需包含大写字母、小写字母、数字、特殊符号中的至少 ${MIN_PASSWORD_CHARACTER_CLASSES} 类`;
  }
  return null;
}
