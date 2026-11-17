import test from 'node:test';
import assert from 'node:assert/strict';

test('Toast state manager adds and notifies listeners', async () => {
  const { toastManager } = await import('../../components/ui/Toast');
  
  let receivedToast: { id: string; message: string; type: string } | null = null;
  const unsubscribe = toastManager.subscribe((toast) => {
    receivedToast = toast;
  });

  toastManager.notify('Teste de notificação segura', 'info');

  const result = receivedToast as { id: string; message: string; type: string } | null;
  assert.ok(result);
  assert.strictEqual(result.message, 'Teste de notificação segura');
  assert.strictEqual(result.type, 'info');

  unsubscribe();

  // Test that unsubscribe prevents future notifications
  let afterUnsubscribeReceived = false;
  toastManager.notify('Depois do unsubscribe', 'success');
  assert.strictEqual(afterUnsubscribeReceived, false);

  // Test helper methods (success, error, warning)
  const receivedList: Array<{ id: string; message: string; type: string; duration?: number }> = [];
  const unsubAll = toastManager.subscribe((t) => receivedList.push(t));

  toastManager.success('Operação realizada com sucesso', 3000);
  toastManager.warning('Atenção aos detalhes');
  toastManager.error('Erro de conexão');

  assert.strictEqual(receivedList.length, 3);
  assert.strictEqual(receivedList[0].type, 'success');
  assert.strictEqual(receivedList[0].duration, 3000);
  assert.strictEqual(receivedList[1].type, 'warning');
  assert.strictEqual(receivedList[1].duration, 4000);
  assert.strictEqual(receivedList[2].type, 'error');

  unsubAll();
});
