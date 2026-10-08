import Link from 'next/link';
import { NexusBlackCard } from '@/components/ui/NexusBlackCard';

export function HeroSection() {
  return (
    <section className="relative w-full overflow-hidden bg-surface pt-space-lg pb-20 sm:pb-24">
      {/* Ambient Atmospheric Glow Orbs */}
      <div className="absolute -top-24 right-1/4 w-[480px] h-[480px] rounded-full bg-gradient-to-br from-secondary/15 via-secondary-fixed/20 to-transparent blur-3xl pointer-events-none -z-0" />
      <div className="absolute top-1/3 -right-10 w-[380px] h-[380px] rounded-full bg-amber-400/10 blur-3xl pointer-events-none -z-0" />
      <div className="absolute top-1/2 -left-20 w-80 h-80 rounded-full bg-surface-container-highest/40 blur-2xl pointer-events-none -z-0" />
      
      <div className="w-full px-margin mx-auto max-w-7xl relative z-10">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-gutter items-center">
          {/* Left Column: Value Proposition & CTAs */}
          <div className="lg:col-span-7 flex flex-col gap-space-md z-10">
            {/* Regulatory & Institutional Live Badge */}
            <div className="inline-flex items-center gap-2 self-start px-3.5 py-1.5 rounded-full bg-surface-container-high/70 border border-surface-container shadow-2xs backdrop-blur-xs">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-secondary opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-secondary"></span>
              </span>
              <span className="font-label-caps text-label-caps text-on-surface-variant uppercase tracking-wider font-semibold">
                Infraestrutura Soberana • Autorizado BACEN
              </span>
            </div>

            {/* Main Headline */}
            <h1 className="font-display-hero text-display-hero text-on-surface tracking-tight">
              A nova era do banking institucional e pessoal.
            </h1>

            {/* Sub-headline */}
            <p className="font-body-lg text-body-lg text-on-surface-variant max-w-xl leading-relaxed">
              Liquidação instantânea, taxa zero de manutenção e segurança criptográfica de classe soberana. O seu patrimônio protegido com garantia ordinária do FGC de até R$ 250 mil.
            </p>

            {/* CTAs */}
            <div className="flex flex-wrap items-center gap-space-md pt-space-xs">
              <Link
                className="inline-flex items-center gap-2 px-7 py-3.5 rounded-full bg-[#0e1c2f] text-white hover:bg-[#1a2d47] transition-all duration-200 shadow-md hover:shadow-xl hover:shadow-black/15 hover:-translate-y-0.5 active:translate-y-0 active:scale-[0.99] group font-semibold text-headline-sm"
                href="/signup"
              >
                <span>Abrir Conta Gratuita</span>
                <span className="material-symbols-outlined text-[20px] no-underline transition-transform duration-200 group-hover:translate-x-1 shrink-0">
                  arrow_forward
                </span>
              </Link>
              <a
                className="inline-flex items-center gap-2 px-6 py-3.5 rounded-full bg-surface-container-lowest border border-surface-container hover:bg-surface-container-low hover:border-outline-variant/60 text-on-surface transition-all duration-200 shadow-2xs hover:shadow-sm hover:-translate-y-0.5 active:translate-y-0 active:scale-[0.99] font-medium text-headline-sm"
                href="#simulador"
              >
                <span>Simular Rendimento</span>
                <span className="material-symbols-outlined text-[18px] text-secondary no-underline">calculate</span>
              </a>
            </div>
            
            {/* Trust Proof Badges */}
            <div className="flex flex-wrap items-center gap-2.5 pt-space-sm text-on-surface-variant">
              <div className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-low/90 border border-surface-container shadow-2xs">
                <span className="material-symbols-outlined text-[16px] text-secondary">verified_user</span>
                <span className="font-body-sm text-body-sm font-medium">Garantido pelo FGC</span>
              </div>
              <div className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-low/90 border border-surface-container shadow-2xs">
                <span className="material-symbols-outlined text-[16px] text-secondary">speed</span>
                <span className="font-body-sm text-body-sm font-medium">Aprovação em 3 min</span>
              </div>
              <div className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-container-low/90 border border-surface-container shadow-2xs">
                <span className="material-symbols-outlined text-[16px] text-secondary">credit_card_off</span>
                <span className="font-body-sm text-body-sm font-medium">Anuidade R$ 0,00</span>
              </div>
            </div>
          </div>
          
          {/* Right Column: Authentic Luxury Black Card & Real-Time Settlement Composite */}
          <div className="lg:col-span-5 relative mt-space-lg lg:mt-0 flex justify-center">
            <div className="relative w-full max-w-[430px] group/hero-card">
              {/* Authentic Nexus Black Physical Card Mockup with 3D Depth Shadow */}
              <div className="relative z-20 w-full drop-shadow-[0_25px_35px_rgba(0,0,0,0.38)] transition-transform duration-500 group-hover/hero-card:-translate-y-1">
                <NexusBlackCard
                  cardHolder="EDUARDO M. SILVA"
                  cardNumber="5428 4182 9901 4182"
                  validThru="09/31"
                  interactive={false}
                />
              </div>
              
              {/* Companion Live Settlement Notification Widget */}
              <div className="relative z-30 mt-3.5 sm:mt-4 mx-1 sm:mx-2 bg-surface-container-lowest/95 backdrop-blur-xl rounded-2xl p-4 sm:p-5 shadow-[0_20px_50px_rgba(0,0,0,0.10),0_0_0_1px_rgba(0,0,0,0.04)] border border-surface-container flex flex-col gap-3 transition-transform duration-500 group-hover/hero-card:translate-y-0.5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="relative flex h-2 w-2">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                      <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                    </span>
                    <span className="font-label-caps text-label-caps text-on-surface uppercase tracking-wider font-semibold">
                      Liquidação Imediata
                    </span>
                  </div>
                  <span className="font-code-sm text-code-sm text-on-surface-variant font-medium">Hoje, 14:28</span>
                </div>

                <div className="flex items-baseline justify-between pt-0.5">
                  <div>
                    <span className="font-label-caps text-label-caps text-on-surface-variant block">
                      Saldo Disponível
                    </span>
                    <span className="font-headline-lg text-headline-lg text-on-surface font-bold tracking-tight font-mono">
                      R$ 15.420,50
                    </span>
                  </div>
                  <div className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-700">
                    <span className="material-symbols-outlined text-[15px]">trending_up</span>
                    <span className="font-code-sm text-code-sm font-semibold font-mono">+34,2% a.a.</span>
                  </div>
                </div>

                <div className="flex items-center justify-between bg-surface-container-low/90 border border-surface-container px-3 py-2 rounded-lg text-xs">
                  <span className="font-body-sm text-body-sm text-on-surface-variant flex items-center gap-1.5 font-medium">
                    <span className="material-symbols-outlined text-[16px] text-secondary">verified</span>
                    Chave Pix Segura
                  </span>
                  <span className="font-code-sm text-code-sm font-semibold text-on-surface">
                    eduardo@nexus.com.br
                  </span>
                </div>
              </div>
              
              {/* Sovereign Security Sticker Seal */}
              <div className="absolute -bottom-3 left-6 z-40 bg-neutral-950/95 backdrop-blur-md text-amber-300 px-3.5 py-1 rounded-full shadow-xl flex items-center gap-2 border border-neutral-700/80">
                <span className="material-symbols-outlined text-[15px] text-amber-300">lock</span>
                <span className="font-code-sm text-code-sm tracking-wider font-semibold text-neutral-200">
                  HSM FIPS 140-2 LEVEL 4
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
