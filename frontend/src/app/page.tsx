import { Button } from '@/modules/shared/components/atoms/Button';
import styles from './page.module.css';

/**
 * Placeholder da raiz. Sai quando a rota de tickets existir — ate la ela serve de
 * prova de que layout, tokens e CSS Modules estao ligados.
 */
export default function HomePage() {
  return (
    <main className={styles.main}>
      <h1 className={styles.title}>Ticket Management System</h1>
      <p className={styles.subtitle}>
        Esqueleto de p&eacute;. Nenhum m&oacute;dulo de dom&iacute;nio foi implementado ainda.
      </p>
      <Button variant="secondary" disabled>
        Abrir chamado
      </Button>
    </main>
  );
}
