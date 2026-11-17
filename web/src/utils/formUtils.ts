export function getPasswordInputType(isVisible: boolean): 'text' | 'password' {
  return isVisible ? 'text' : 'password';
}

export function getPasswordToggleAriaLabel(fieldLabel: string, isVisible: boolean): string {
  return isVisible ? `Ocultar ${fieldLabel}` : `Exibir ${fieldLabel}`;
}
