/**
 * MediAI - 하단 탭바 컴포넌트
 * 탭: 홈, 내약, 기록, AI
 */
import { motion } from 'framer-motion';
import { Home, Pill, CalendarDays, Sparkles } from 'lucide-react';

interface Tab {
  id: string;
  label: string;
  icon: React.ReactNode;
}

const TABS: Tab[] = [
  { id: 'home', label: '홈', icon: <Home size={22} strokeWidth={2} /> },
  { id: 'mymed', label: '내약', icon: <Pill size={22} strokeWidth={2} /> },
  { id: 'record', label: '기록', icon: <CalendarDays size={22} strokeWidth={2} /> },
  { id: 'ai', label: 'AI', icon: <Sparkles size={22} strokeWidth={2} /> },
];

interface BottomTabBarProps {
  activeTab: string;
  onTabChange: (tab: string) => void;
}

export default function BottomTabBar({ activeTab, onTabChange }: BottomTabBarProps) {
  return (
    <div className="bottom-tab-bar">
      {TABS.map(tab => {
        const isActive = activeTab === tab.id;
        return (
          <button
            key={tab.id}
            onClick={() => onTabChange(tab.id)}
            className="tab-item relative"
          >
            {isActive && (
              <motion.div
                layoutId="tab-indicator"
                className="absolute -top-1 left-1/2 -translate-x-1/2 w-1 h-1 bg-blue-500 rounded-full"
                transition={{ type: 'spring', stiffness: 400, damping: 30 }}
              />
            )}
            <motion.div
              animate={{
                color: isActive ? '#4A90E2' : '#9BA8BB',
                scale: isActive ? 1.1 : 1,
              }}
              transition={{ duration: 0.2 }}
            >
              {tab.icon}
            </motion.div>
            <motion.span
              animate={{
                color: isActive ? '#4A90E2' : '#9BA8BB',
                fontWeight: isActive ? 700 : 500,
              }}
              className="text-xs"
              style={{ fontSize: '11px' }}
            >
              {tab.label}
            </motion.span>
          </button>
        );
      })}
    </div>
  );
}
