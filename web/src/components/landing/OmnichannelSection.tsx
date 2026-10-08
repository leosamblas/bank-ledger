import Link from 'next/link';

export function OmnichannelSection() {
  return (
    <section className="w-full py-space-xl bg-surface scroll-mt-24 md:scroll-mt-28" id="omnichannel">
      <div className="w-full px-margin mx-auto max-w-7xl">
        <div className="text-center max-w-2xl mx-auto mb-space-xl">
          <span className="font-label-caps text-label-caps uppercase tracking-wider text-secondary">Experiência Omnichannel</span>
          <h2 className="font-headline-lg text-headline-lg text-on-surface mt-space-xs">
            Controle cirúrgico no navegador ou na palma da mão.
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant mt-space-xs">
            Uma plataforma unificada com extratos ricos em metadados, comprovantes instantâneos e parâmetros operacionais sob sua tutela.
          </p>
        </div>
        
        {/* Ledger Dashboard Preview Mockup */}
        <div className="bg-surface-container-lowest rounded-xl shadow-lg p-space-lg mb-space-xl overflow-hidden">
          <div className="flex flex-wrap items-center justify-between pb-space-md mb-space-md bg-surface-container-low p-space-sm rounded-lg">
            <div className="flex items-center gap-space-sm">
              <div className="flex gap-1.5">
                <div className="w-3 h-3 rounded-full bg-surface-container-highest"></div>
                <div className="w-3 h-3 rounded-full bg-surface-container-highest"></div>
                <div className="w-3 h-3 rounded-full bg-surface-container-highest"></div>
              </div>
              <span className="font-code-sm text-code-sm text-on-surface-variant ml-2">app.nexusbank.com.br/core-ledger</span>
            </div>
            <div className="flex items-center gap-space-md">
              <span className="font-label-caps text-label-caps text-on-surface-variant">SESSÃO CRIPTOGRAFADA: TLS 1.3</span>
              <div className="w-2 h-2 rounded-full bg-secondary"></div>
            </div>
          </div>
          
          <div className="overflow-x-auto">
            <table className="w-full text-left font-body-sm text-body-sm">
              <thead>
                <tr className="bg-surface-container-low text-on-surface-variant font-label-caps text-label-caps uppercase tracking-wider">
                  <th className="py-space-sm px-space-md">Data / Registro</th>
                  <th className="py-space-sm px-space-md">Identificador • Contraparte</th>
                  <th className="py-space-sm px-space-md">Tipo</th>
                  <th className="py-space-sm px-space-md text-right">Valor</th>
                  <th className="py-space-sm px-space-md text-right">Saldo Contábil</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-surface-container">
                <tr className="hover:bg-surface-container-low transition-colors">
                  <td className="py-space-sm px-space-md font-code-sm text-code-sm text-on-surface">14/10 14:12:08</td>
                  <td className="py-space-sm px-space-md">
                    <div className="font-code-sm text-code-sm text-on-surface font-semibold">ALIANÇA TECNOLOGIA LTDA</div>
                    <span className="font-code-sm text-code-sm text-on-surface-variant">ISPB 04189920 - TXID #9048-A1</span>
                  </td>
                  <td className="py-space-sm px-space-md">
                    <span className="px-space-sm py-0.5 rounded text-label-caps font-label-caps bg-surface-container text-on-surface">PIX RECEBIDO</span>
                  </td>
                  <td className="py-space-sm px-space-md text-right font-code-md text-code-md font-semibold text-on-surface">
                    + R$ 8.500,00
                  </td>
                  <td className="py-space-sm px-space-md text-right font-code-sm text-code-sm text-on-surface-variant">
                    R$ 15.420,50
                  </td>
                </tr>
                <tr className="hover:bg-surface-container-low transition-colors">
                  <td className="py-space-sm px-space-md font-code-sm text-code-sm text-on-surface">14/10 11:45:22</td>
                  <td className="py-space-sm px-space-md">
                    <div className="font-code-sm text-code-sm text-on-surface font-semibold">AMAZON WEB SERVICES BRASIL</div>
                    <span className="font-code-sm text-code-sm text-on-surface-variant">CARTÃO VIRTUAL DEDICADO #8812</span>
                  </td>
                  <td className="py-space-sm px-space-md">
                    <span className="px-space-sm py-0.5 rounded text-label-caps font-label-caps bg-surface-container text-on-surface-variant">DÉBITO CARTÃO</span>
                  </td>
                  <td className="py-space-sm px-space-md text-right font-code-md text-code-md font-semibold text-on-surface-variant">
                    - R$ 1.240,80
                  </td>
                  <td className="py-space-sm px-space-md text-right font-code-sm text-code-sm text-on-surface-variant">
                    R$ 6.920,50
                  </td>
                </tr>
                <tr className="hover:bg-surface-container-low transition-colors">
                  <td className="py-space-sm px-space-md font-code-sm text-code-sm text-on-surface">14/10 00:05:00</td>
                  <td className="py-space-sm px-space-md">
                    <div className="font-code-sm text-code-sm text-on-surface font-semibold">RENDIMENTO AUTOMÁTICO 102% CDI</div>
                    <span className="font-code-sm text-code-sm text-on-surface-variant">CRÉDITO DE JUROS SOBERANOS</span>
                  </td>
                  <td className="py-space-sm px-space-md">
                    <span className="px-space-sm py-0.5 rounded text-label-caps font-label-caps bg-surface-container text-secondary font-semibold">LIQUIDAÇÃO CDI</span>
                  </td>
                  <td className="py-space-sm px-space-md text-right font-code-md text-code-md font-semibold text-on-surface">
                    + R$ 42,30
                  </td>
                  <td className="py-space-sm px-space-md text-right font-code-sm text-code-sm text-on-surface-variant">
                    R$ 8.161,30
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div className="pt-4 mt-2 border-t border-surface-container flex flex-col sm:flex-row items-center justify-between gap-3">
            <span className="font-body-sm text-body-sm text-on-surface-variant">
              Livro-razão institucional com hash criptográfico e validação em tempo real.
            </span>
            <Link
              href="/signup"
              className="px-4 py-2 rounded-lg bg-primary text-on-primary hover:bg-neutral-800 font-headline-sm text-[13px] font-semibold transition-colors flex items-center gap-1.5 shadow-xs"
            >
              <span>Abrir Conta e Começar</span>
              <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
            </Link>
          </div>
        </div>
        
        {/* Testimonials Mosaic */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-gutter">
          <div className="p-space-lg rounded-xl bg-surface-container-low shadow-sm flex flex-col justify-between">
            <p className="font-body-md text-body-md text-on-surface italic mb-space-md">
              &quot;A liquidação imediata de transferências volumosas e a visualização limpa do extrato nos economizaram dezenas de horas na reconciliação financeira mensal.&quot;
            </p>
            <div className="flex items-center gap-space-sm">
              <div className="w-10 h-10 rounded-full bg-surface-container flex items-center justify-center font-headline-sm text-headline-sm text-on-surface">
                MR
              </div>
              <div>
                <span className="font-headline-sm text-headline-sm text-on-surface block">Mariana Rezende</span>
                <span className="font-body-sm text-body-sm text-on-surface-variant">Diretora Financeira, Vertex Log</span>
              </div>
            </div>
          </div>
          <div className="p-space-lg rounded-xl bg-surface-container-low shadow-sm flex flex-col justify-between">
            <p className="font-body-md text-body-md text-on-surface italic mb-space-md">
              &quot;A segurança de ter proteção FGC aliada a um rendimento superior à poupança sem qualquer esforço é o que tornou o Nexus a conta principal da minha família.&quot;
            </p>
            <div className="flex items-center gap-space-sm">
              <div className="w-10 h-10 rounded-full bg-surface-container flex items-center justify-center font-headline-sm text-headline-sm text-on-surface">
                TC
              </div>
              <div>
                <span className="font-headline-sm text-headline-sm text-on-surface block">Thiago Castanho</span>
                <span className="font-body-sm text-body-sm text-on-surface-variant">Investidor Autônomo</span>
              </div>
            </div>
          </div>
          <div className="p-space-lg rounded-xl bg-surface-container-low shadow-sm flex flex-col justify-between">
            <p className="font-body-md text-body-md text-on-surface italic mb-space-md">
              &quot;Atendimento de verdade quando precisei ajustar limites para compras internacionais de hardware. Em menos de 2 minutos fui atendido com precisão técnica.&quot;
            </p>
            <div className="flex items-center gap-space-sm">
              <div className="w-10 h-10 rounded-full bg-surface-container flex items-center justify-center font-headline-sm text-headline-sm text-on-surface">
                CP
              </div>
              <div>
                <span className="font-headline-sm text-headline-sm text-on-surface block">Carolina Prado</span>
                <span className="font-body-sm text-body-sm text-on-surface-variant">CTO, Nexus Cloud Labs</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
