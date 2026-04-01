/**
 * MediAI - 홈 화면
 * 디자인: 모던 플랫 헬스케어
 * - 오늘의 약 복용 현황 (파란 그라디언트 카드)
 * - 다음 복용 예정 약
 * - 오늘의 약 목록
 */
import { motion, AnimatePresence } from 'framer-motion';
import { Check, Bell } from 'lucide-react';
import { useMed } from '@/contexts/MedContext';
import { toast } from 'sonner';

export default function HomePage() {
  const { medicines, todayDate, takeMedicine, untakeMedicine, takenCount, totalCount, nextMedicine, timeIcons } = useMed();

  const progressPercent = totalCount > 0 ? Math.round((takenCount / totalCount) * 100) : 0;

  const handleTake = (id: string, name: string, taken: boolean) => {
    if (taken) {
      untakeMedicine(id);
      toast.info(`${name} 복용 취소`);
    } else {
      takeMedicine(id);
      toast.success(`${name} 복용 완료! 💊`);
    }
  };

  return (
    <div className="scroll-content px-4 pt-4">
      {/* 헤더 */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <p className="text-sm text-gray-500 font-medium">좋은 아침이에요 🌅</p>
          <h1 className="text-2xl font-bold text-gray-800 mt-0.5">오늘의 약 복용</h1>
        </div>
        <div className="flex items-center gap-2">
          <div className="bg-white rounded-xl px-3 py-1.5 shadow-sm border border-blue-100">
            <span className="text-sm font-semibold text-blue-500">{todayDate}</span>
          </div>
          <button className="w-9 h-9 bg-white rounded-xl flex items-center justify-center shadow-sm border border-blue-100">
            <Bell size={16} className="text-gray-500" />
          </button>
        </div>
      </div>

      {/* 복용 현황 카드 */}
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className="blue-gradient-card rounded-2xl p-5 mb-4 shadow-md"
      >
        <div className="flex items-start justify-between mb-3">
          <div>
            <p className="text-white/80 text-sm font-medium mb-1">오늘 복용 현황</p>
            <p className="text-white text-2xl font-bold">
              {takenCount}/{totalCount}회 완료
            </p>
          </div>
          <div className="bg-white/20 rounded-xl px-3 py-1.5">
            <span className="text-white font-bold text-lg">{progressPercent}%</span>
          </div>
        </div>
        <div className="progress-bar-track">
          <motion.div
            className="progress-bar-fill"
            initial={{ width: 0 }}
            animate={{ width: `${progressPercent}%` }}
            transition={{ duration: 0.8, ease: 'easeOut' }}
          />
        </div>
        {progressPercent === 100 && (
          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="text-white/90 text-sm mt-2 font-medium"
          >
            🎉 오늘 모든 약을 복용했어요!
          </motion.p>
        )}
      </motion.div>

      {/* 다음 복용 예정 */}
      {nextMedicine && (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.1 }}
          className="bg-white rounded-2xl p-4 mb-5 shadow-sm border border-blue-50"
        >
          <p className="text-xs text-gray-400 font-medium mb-2">다음 복용 예정</p>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-blue-50 rounded-xl flex items-center justify-center text-lg">
                {timeIcons[nextMedicine.time]}
              </div>
              <div>
                <p className="font-bold text-gray-800 text-base">{nextMedicine.name}</p>
                <p className="text-xs text-gray-400 mt-0.5">
                  <span className="mr-1">{timeIcons[nextMedicine.time]}</span>
                  {nextMedicine.time}
                </p>
              </div>
            </div>
            <button
              onClick={() => handleTake(nextMedicine.id, nextMedicine.name, nextMedicine.taken)}
              className="bg-blue-500 text-white rounded-xl px-5 py-2 text-sm font-semibold hover:bg-blue-600 transition-colors"
            >
              복용
            </button>
          </div>
        </motion.div>
      )}

      {/* 오늘의 약 목록 */}
      <div className="mb-4">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-base font-bold text-gray-800">오늘의 약 목록</h2>
          <span className="text-sm text-gray-400">{totalCount}개</span>
        </div>

        <div className="flex flex-col gap-2.5">
          <AnimatePresence>
            {medicines.map((med, idx) => (
              <motion.div
                key={med.id}
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.3, delay: idx * 0.05 }}
                className="med-item"
                style={{ opacity: med.taken ? 0.7 : 1 }}
              >
                {/* 체크박스 */}
                <button
                  onClick={() => handleTake(med.id, med.name, med.taken)}
                  className={`round-checkbox ${med.taken ? 'checked' : ''}`}
                >
                  {med.taken && (
                    <motion.div
                      initial={{ scale: 0 }}
                      animate={{ scale: 1 }}
                      transition={{ type: 'spring', stiffness: 400, damping: 20 }}
                    >
                      <Check size={13} className="text-white" strokeWidth={3} />
                    </motion.div>
                  )}
                </button>

                {/* 약 정보 */}
                <div className="flex-1 min-w-0">
                  <p className={`font-semibold text-sm ${med.taken ? 'text-gray-400 line-through' : 'text-gray-800'}`}>
                    {med.name}
                  </p>
                  <p className="text-xs text-gray-400 mt-0.5">
                    <span className="mr-1">{timeIcons[med.time]}</span>
                    {med.time}
                  </p>
                </div>

                {/* 복용 버튼 */}
                <button
                  onClick={() => handleTake(med.id, med.name, med.taken)}
                  className={`take-btn ${med.taken ? 'taken' : ''}`}
                >
                  {med.taken ? '완료' : '복용'}
                </button>
              </motion.div>
            ))}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
}
