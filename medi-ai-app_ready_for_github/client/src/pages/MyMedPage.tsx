/**
 * MediAI - 내약 화면
 * 디자인: 모던 플랫 헬스케어
 * - 등록된 약 목록 관리
 * - 약 추가/삭제 기능
 */
import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Plus, Trash2, Pill, X } from 'lucide-react';
import { useMed, TimeOfDay } from '@/contexts/MedContext';
import { toast } from 'sonner';

const TIME_OPTIONS: TimeOfDay[] = ['아침', '점심', '저녁', '취침 전'];
const COLOR_OPTIONS = ['#FF6B6B', '#FFD93D', '#6BCB77', '#4A90E2', '#9B59B6', '#E67E22', '#1ABC9C', '#E91E63'];

export default function MyMedPage() {
  const { medicines, addMedicine, removeMedicine, timeIcons } = useMed();
  const [showAddModal, setShowAddModal] = useState(false);
  const [form, setForm] = useState({
    name: '',
    time: '아침' as TimeOfDay,
    dosage: '',
    color: '#4A90E2',
    icon: '💊',
  });

  const handleAdd = () => {
    if (!form.name.trim()) {
      toast.error('약 이름을 입력해주세요');
      return;
    }
    addMedicine(form);
    toast.success(`${form.name} 추가 완료!`);
    setShowAddModal(false);
    setForm({ name: '', time: '아침', dosage: '', color: '#4A90E2', icon: '💊' });
  };

  const handleRemove = (id: string, name: string) => {
    removeMedicine(id);
    toast.info(`${name} 삭제됨`);
  };

  // 시간대별 그룹핑
  const grouped = TIME_OPTIONS.reduce((acc, time) => {
    acc[time] = medicines.filter(m => m.time === time);
    return acc;
  }, {} as Record<TimeOfDay, typeof medicines>);

  return (
    <div className="scroll-content px-4 pt-4">
      {/* 헤더 */}
      <div className="flex items-center justify-between mb-5">
        <div>
          <h1 className="text-2xl font-bold text-gray-800">내 약</h1>
          <p className="text-sm text-gray-400 mt-0.5">총 {medicines.length}개 등록됨</p>
        </div>
        <button
          onClick={() => setShowAddModal(true)}
          className="flex items-center gap-1.5 bg-blue-500 text-white rounded-xl px-4 py-2 text-sm font-semibold hover:bg-blue-600 transition-colors shadow-sm"
        >
          <Plus size={16} />
          약 추가
        </button>
      </div>

      {/* 시간대별 약 목록 */}
      <div className="flex flex-col gap-5">
        {TIME_OPTIONS.map(time => (
          <div key={time}>
            <div className="flex items-center gap-2 mb-2.5">
              <span className="text-base">{timeIcons[time]}</span>
              <h2 className="text-sm font-bold text-gray-600">{time}</h2>
              <span className="text-xs text-gray-400">({grouped[time].length}개)</span>
            </div>

            {grouped[time].length === 0 ? (
              <div className="bg-white rounded-2xl p-4 text-center border border-dashed border-blue-100">
                <p className="text-sm text-gray-300">등록된 약이 없어요</p>
              </div>
            ) : (
              <div className="flex flex-col gap-2">
                {grouped[time].map((med, idx) => (
                  <motion.div
                    key={med.id}
                    initial={{ opacity: 0, x: -8 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: idx * 0.05 }}
                    className="bg-white rounded-2xl p-4 flex items-center gap-3 shadow-sm border border-blue-50"
                  >
                    <div
                      className="w-10 h-10 rounded-xl flex items-center justify-center text-lg flex-shrink-0"
                      style={{ backgroundColor: med.color + '20' }}
                    >
                      {med.icon}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="font-semibold text-gray-800 text-sm">{med.name}</p>
                      {med.dosage && (
                        <p className="text-xs text-gray-400 mt-0.5">{med.dosage}</p>
                      )}
                    </div>
                    <div
                      className="w-3 h-3 rounded-full flex-shrink-0"
                      style={{ backgroundColor: med.color }}
                    />
                    <button
                      onClick={() => handleRemove(med.id, med.name)}
                      className="w-8 h-8 flex items-center justify-center rounded-xl hover:bg-red-50 transition-colors"
                    >
                      <Trash2 size={15} className="text-gray-300 hover:text-red-400 transition-colors" />
                    </button>
                  </motion.div>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>

      {/* 약 추가 모달 */}
      <AnimatePresence>
        {showAddModal && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="fixed inset-0 bg-black/40 z-50"
              onClick={() => setShowAddModal(false)}
            />
            <motion.div
              initial={{ opacity: 0, y: 60 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 60 }}
              transition={{ type: 'spring', damping: 25, stiffness: 300 }}
              className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-[430px] bg-white rounded-t-3xl p-6 z-50 shadow-2xl"
            >
              <div className="flex items-center justify-between mb-5">
                <h3 className="text-lg font-bold text-gray-800">새 약 추가</h3>
                <button
                  onClick={() => setShowAddModal(false)}
                  className="w-8 h-8 flex items-center justify-center rounded-xl bg-gray-100 hover:bg-gray-200 transition-colors"
                >
                  <X size={16} className="text-gray-500" />
                </button>
              </div>

              <div className="flex flex-col gap-4">
                {/* 약 이름 */}
                <div>
                  <label className="text-xs font-semibold text-gray-500 mb-1.5 block">약 이름 *</label>
                  <input
                    type="text"
                    value={form.name}
                    onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                    placeholder="예: 타이레놀"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-3 text-sm text-gray-800 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 transition-all"
                  />
                </div>

                {/* 복용 시간 */}
                <div>
                  <label className="text-xs font-semibold text-gray-500 mb-1.5 block">복용 시간</label>
                  <div className="grid grid-cols-4 gap-2">
                    {TIME_OPTIONS.map(t => (
                      <button
                        key={t}
                        onClick={() => setForm(f => ({ ...f, time: t }))}
                        className={`py-2 rounded-xl text-xs font-semibold transition-all ${
                          form.time === t
                            ? 'bg-blue-500 text-white'
                            : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
                        }`}
                      >
                        {t}
                      </button>
                    ))}
                  </div>
                </div>

                {/* 용량 */}
                <div>
                  <label className="text-xs font-semibold text-gray-500 mb-1.5 block">용량 (선택)</label>
                  <input
                    type="text"
                    value={form.dosage}
                    onChange={e => setForm(f => ({ ...f, dosage: e.target.value }))}
                    placeholder="예: 500mg"
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-3 text-sm text-gray-800 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 transition-all"
                  />
                </div>

                {/* 색상 선택 */}
                <div>
                  <label className="text-xs font-semibold text-gray-500 mb-1.5 block">색상</label>
                  <div className="flex gap-2 flex-wrap">
                    {COLOR_OPTIONS.map(color => (
                      <button
                        key={color}
                        onClick={() => setForm(f => ({ ...f, color }))}
                        className="w-8 h-8 rounded-full transition-all"
                        style={{
                          backgroundColor: color,
                          outline: form.color === color ? `3px solid ${color}` : 'none',
                          outlineOffset: '2px',
                        }}
                      />
                    ))}
                  </div>
                </div>

                <button
                  onClick={handleAdd}
                  className="w-full bg-blue-500 text-white rounded-xl py-3.5 font-bold text-sm hover:bg-blue-600 transition-colors mt-1"
                >
                  추가하기
                </button>
              </div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
}
