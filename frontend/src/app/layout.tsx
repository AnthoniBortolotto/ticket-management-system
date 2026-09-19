import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import '@/styles/globals.css';

export const metadata: Metadata = {
  title: {
    default: 'Ticket Management System',
    template: '%s · Ticket Management System',
  },
  description: 'Helpdesk com atribuicao por equipe, visibilidade por papel e SLA em horario util.',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
