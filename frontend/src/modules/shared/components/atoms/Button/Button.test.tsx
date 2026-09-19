import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Button } from './Button';

describe('Button', () => {
  it('rende o rotulo que recebeu', () => {
    render(<Button>Abrir chamado</Button>);

    expect(screen.getByRole('button', { name: 'Abrir chamado' })).toBeInTheDocument();
  });

  it('nasce type="button" para nao submeter formulario sem querer', () => {
    render(<Button>Cancelar</Button>);

    expect(screen.getByRole('button')).toHaveAttribute('type', 'button');
  });

  it('aciona o clique', async () => {
    const aoClicar = vi.fn();
    render(<Button onClick={aoClicar}>Resolver</Button>);

    await userEvent.click(screen.getByRole('button'));

    expect(aoClicar).toHaveBeenCalledOnce();
  });

  it('nao aciona o clique quando desabilitado', async () => {
    const aoClicar = vi.fn();
    render(
      <Button disabled onClick={aoClicar}>
        Resolver
      </Button>,
    );

    await userEvent.click(screen.getByRole('button'));

    expect(aoClicar).not.toHaveBeenCalled();
  });
});
