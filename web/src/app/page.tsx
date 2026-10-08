import { LandingHeader } from '@/components/landing/LandingHeader';
import { HeroSection } from '@/components/landing/HeroSection';
import { TrustTicker } from '@/components/landing/TrustTicker';
import { CoreFeatures } from '@/components/landing/CoreFeatures';
import { Simulator } from '@/components/landing/Simulator';
import { OmnichannelSection } from '@/components/landing/OmnichannelSection';
import { FaqSection } from '@/components/landing/FaqSection';
import { CtaSection } from '@/components/landing/CtaSection';
import { LandingFooter } from '@/components/landing/LandingFooter';

export default function Home() {
  return (
    <>
      <LandingHeader />
      <main className="w-full pt-24 md:pt-28 bg-background flex flex-col">
        <HeroSection />
        <TrustTicker />
        <CoreFeatures />
        <Simulator />
        <OmnichannelSection />
        <FaqSection />
        <CtaSection />
      </main>
      <LandingFooter />
    </>
  );
}
