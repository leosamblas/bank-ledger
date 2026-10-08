const features = [
  {
    icon: "account_balance_wallet",
    title: "Conta Isenta de Tarifas",
    description:
      "Pix ilimitado 24/7, TED sem custo e manutenção zero. Seu dinheiro com rentabilidade automática diária a 102% do CDI.",
    badgeIcon: "verified",
    badgeText: "SEM TARIFAS OCULTAS",
  },
  {
    icon: "credit_card",
    title: "Cartão Nexus Black",
    description:
      "Metal refinado, bandeira global e zero anuidade. Gestão instantânea de limites e cartões virtuais dinâmicos para compras online.",
    badgeIcon: "contactless",
    badgeText: "CONTACTLESS",
  },
  {
    icon: "enhanced_encryption",
    title: "Blindagem Soberana",
    description:
      "Biometria de presença, módulo HSM corporativo e proteção anti-fraude preditiva com análise em menos de 12 milissegundos.",
    badgeIcon: "lock",
    badgeText: "CRIPTOGRAFIA FIPS 140-2",
  },
  {
    icon: "support_agent",
    title: "Atendimento Humano 24/7",
    description:
      "Sem labirintos de robôs. Fale com especialistas financeiros prontos para solucionar suas demandas a qualquer hora do dia ou da noite.",
    badgeIcon: "schedule",
    badgeText: "TEMPO MÉDIO: 42 SEGUNDOS",
  },
];

export function CoreFeatures() {
  return (
    <section className="w-full py-space-xl bg-surface scroll-mt-24 md:scroll-mt-28" id="recursos">
      <div className="w-full px-margin mx-auto max-w-7xl">
        <div className="flex flex-col md:flex-row md:items-end justify-between mb-space-lg">
          <div className="max-w-xl">
            <span className="font-label-caps text-label-caps uppercase tracking-wider text-secondary">
              Arquitetura de Precisão
            </span>
            <h2 className="font-headline-lg text-headline-lg text-on-surface mt-space-xs">
              Desenhado para quem exige solidez e controle absoluto.
            </h2>
          </div>
          <p className="font-body-md text-body-md text-on-surface-variant max-w-md mt-space-sm md:mt-0">
            Eliminamos burocracias e fricções legadas com processamento de livros-razão de baixa latência e total transparência.
          </p>
        </div>
        
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-gutter items-stretch">
          {features.map((feature) => (
            <div
              key={feature.title}
              className="h-full bg-surface-container-lowest p-space-lg rounded-xl shadow-sm flex flex-col justify-between hover:shadow-md transition-shadow group"
            >
              <div className="flex-1 flex flex-col">
                <div className="w-12 h-12 rounded-lg bg-surface-container flex items-center justify-center text-primary-container mb-space-md shrink-0">
                  <span className="material-symbols-outlined text-[28px]">{feature.icon}</span>
                </div>
                <h3 className="font-headline-sm text-headline-sm text-on-surface mb-space-xs font-semibold">
                  {feature.title}
                </h3>
                <p className="font-body-sm text-body-sm text-on-surface-variant leading-relaxed min-h-[4rem]">
                  {feature.description}
                </p>
              </div>

              <div className="mt-space-lg pt-space-md border-t border-surface-container">
                <div className="w-full h-10 px-3 rounded-lg bg-surface-container-low border border-surface-container flex items-center gap-2 text-on-surface group-hover:border-secondary/40 group-hover:bg-surface-container/60 transition-all">
                  <span className="material-symbols-outlined text-[16px] text-secondary shrink-0">
                    {feature.badgeIcon}
                  </span>
                  <span className="font-code-sm text-code-sm font-semibold uppercase tracking-wider truncate">
                    {feature.badgeText}
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
