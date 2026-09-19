import type { ButtonHTMLAttributes, ReactNode } from 'react';
import styles from './styles.module.css';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  children: ReactNode;
}

/**
 * Atom: nao carrega regra de negocio e nao sabe o que o clique faz.
 *
 * Nao e um Client Component: um `<button>` sem handler renderiza no servidor. Quem
 * passa `onClick` e que precisa do `'use client'`, e a fronteira fica la — o mais
 * baixo possivel na arvore.
 */
export function Button({ variant = 'primary', type = 'button', className, ...props }: ButtonProps) {
  const classes = [styles.button, styles[variant], className].filter(Boolean).join(' ');
  return <button type={type} className={classes} {...props} />;
}
