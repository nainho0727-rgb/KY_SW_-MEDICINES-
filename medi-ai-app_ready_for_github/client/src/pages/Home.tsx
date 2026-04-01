/**
 * MediAI - 메인 앱 레이아웃
 * 디자인: 모던 플랫 헬스케어
 * - 모바일 앱 컨테이너 (최대 430px)
 * - 하단 탭 네비게이션
 * - 페이지 전환 애니메이션
 */
import { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { MedProvider } from '@/contexts/MedContext';
import BottomTabBar from '@/components/BottomTabBar';
import HomePage from './HomePage';
import MyMedPage from './MyMedPage';
import RecordPage from './RecordPage';
import AIPage from './AIPage';

const PAGE_COMPONENTS: Record<string, React.ComponentType> = {
  home: HomePage,
  mymed: MyMedPage,
  record: RecordPage,
  ai: AIPage,
};

export default function Home() {
  const [activeTab, setActiveTab] = useState('home');
  const [prevTab, setPrevTab] = useState('home');

  const handleTabChange = (tab: string) => {
    setPrevTab(activeTab);
    setActiveTab(tab);
  };

  const PageComponent = PAGE_COMPONENTS[activeTab];

  return (
    <MedProvider>
      {/* 배경 - 데스크탑에서 앱처럼 보이게 */}
      <div
        className="min-h-screen flex items-center justify-center"
        style={{ background: 'linear-gradient(135deg, #E8F0FE 0%, #F0F4FF 50%, #E8F0FE 100%)' }}
      >
        {/* 앱 컨테이너 */}
        <div
          className="app-container relative shadow-2xl"
          style={{
            boxShadow: '0 20px 60px rgba(74, 144, 226, 0.15), 0 4px 20px rgba(0,0,0,0.08)',
          }}
        >
          {/* 페이지 콘텐츠 */}
          <AnimatePresence mode="wait">
            <motion.div
              key={activeTab}
              initial={{ opacity: 0, x: activeTab > prevTab ? 20 : -20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: activeTab > prevTab ? -20 : 20 }}
              transition={{ duration: 0.2, ease: 'easeInOut' }}
              className="h-full"
            >
              <PageComponent />
            </motion.div>
          </AnimatePresence>

          {/* 하단 탭바 */}
          <BottomTabBar activeTab={activeTab} onTabChange={handleTabChange} />
        </div>
      </div>
    </MedProvider>
  );
}
