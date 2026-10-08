export function TrustTicker() {
  return (
    <section className="w-full bg-surface-container-low py-space-md shadow-sm">
      <div className="w-full px-margin mx-auto max-w-7xl">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-gutter text-center items-center">
          <div className="flex flex-col">
            <span className="font-code-lg text-code-lg text-on-surface font-semibold">100% REGULADO</span>
            <span className="font-body-sm text-body-sm text-on-surface-variant">Banco Central do Brasil</span>
          </div>
          <div className="flex flex-col">
            <span className="font-code-lg text-code-lg text-on-surface font-semibold">R$ 250 MIL</span>
            <span className="font-body-sm text-body-sm text-on-surface-variant">Proteção Integral FGC</span>
          </div>
          <div className="flex flex-col">
            <span className="font-code-lg text-code-lg text-on-surface font-semibold">+ R$ 2.4 BI</span>
            <span className="font-body-sm text-body-sm text-on-surface-variant">Ativos sob Custódia</span>
          </div>
          <div className="flex flex-col">
            <span className="font-code-lg text-code-lg text-on-surface font-semibold">4.9 / 5.0</span>
            <span className="font-body-sm text-body-sm text-on-surface-variant">+180 mil Avaliações</span>
          </div>
        </div>
      </div>
    </section>
  );
}
