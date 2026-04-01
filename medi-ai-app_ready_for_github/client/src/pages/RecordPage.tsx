/**
 * MediAI - 기록 화면
 * 디자인: 모던 플랫 헬스케어
 * - 주간 복용 달력
 * - 날짜별 복용 기록
 */
import { useState } from 'react';
import { motion } from 'framer-motion';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useMed } from '@/contexts/MedContext';

function getWeekDates(baseDate: Date) {
  const week = [];
  const start = new Date(baseDate);
  const day = start.getDay();
  start.setDate(start.getDate() - day);
  for (let i = 0; i < 7; i++) {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    week.push(d);
  }
  return week;
}

const DAY_LABELS = ['일', '월', '화', '수', '목', '금', '토'];

export default function RecordPage() {
  const { records, medicines, timeIcons } = useMed();
  const [baseDate, setBaseDate] = useState(new Date());
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0]);

  const weekDates = getWeekDates(baseDate);
  const today = new Date().toISOString().split('T')[0];

  const getRecord = (dateStr: string) => records.find(r => r.date === dateStr);

  const selectedRecord = getRecord(selectedDate);

  // 이번 주 통계
  const weekStats = weekDates.map(d => {
    const dateStr = d.toISOString().split('T')[0];
    const rec = getRecord(dateStr);
    if (!rec) return { date: dateStr, percent: dateStr === today ? 0 : null };
    return { date: dateStr, percent: Math.round((rec.takenCount / rec.totalCount) * 100) };
  });

  const prevWeek = () => {
    const d = new Date(baseDate);
    d.setDate(d.getDate() - 7);
    setBaseDate(d);
  };

  const nextWeek = () => {
    const d = new Date(baseDate);
    d.setDate(d.getDate() + 7);
    setBaseDate(d);
  };

  const formatDateLabel = (date: Date) => {
    return `${date.getMonth() + 1}월 ${date.getDate()}일`;
  };

  const weekLabel = `${weekDates[0].getMonth() + 1}월 ${weekDates[0].getDate()}일 ~ ${weekDates[6].getMonth() + 1}월 ${weekDates[6].getDate()}일`;

  return (
    <div className="scroll-content px-4 pt-4">
      {/* 헤더 */}
      <div className="mb-5">
        <h1 className="text-2xl font-bold text-gray-800">복용 기록</h1>
        <p className="text-sm text-gray-400 mt-0.5">날짜별 복용 현황을 확인하세요</p>
      </div>

      {/* 주간 캘린더 */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="bg-white rounded-2xl p-4 mb-4 shadow-sm border border-blue-50"
      >
        <div className="flex items-center justify-between mb-3">
          <button onClick={prevWeek} className="w-8 h-8 flex items-center justify-center rounded-xl hover:bg-gray-100 transition-colors">
            <ChevronLeft size={18} className="text-gray-500" />
          </button>
          <span className="text-sm font-semibold text-gray-600">{weekLabel}</span>
          <button onClick={nextWeek} className="w-8 h-8 flex items-center justify-center rounded-xl hover:bg-gray-100 transition-colors">
            <ChevronRight size={18} className="text-gray-500" />
          </button>
        </div>

        <div className="grid grid-cols-7 gap-1">
          {weekDates.map((date, idx) => {
            const dateStr = date.toISOString().split('T')[0];
            const stat = weekStats[idx];
            const isToday = dateStr === today;
            const isSelected = dateStr === selectedDate;
            const isFuture = dateStr > today;
            const percent = stat.percent;

            return (
              <button
                key={dateStr}
                onClick={() => setSelectedDate(dateStr)}
                className={`flex flex-col items-center gap-1 py-2 rounded-xl transition-all ${
                  isSelected ? 'bg-blue-500' : 'hover:bg-gray-50'
                }`}
              >
                <span className={`text-xs font-medium ${isSelected ? 'text-white/80' : 'text-gray-400'}`}>
                  {DAY_LABELS[idx]}
                </span>
                <span className={`text-sm font-bold ${isSelected ? 'text-white' : isToday ? 'text-blue-500' : 'text-gray-700'}`}>
                  {date.getDate()}
                </span>
                {/* 복용률 도트 */}
                <div className={`w-5 h-5 rounded-full flex items-center justify-center ${
                  isFuture ? 'bg-gray-100' :
                  percent === null ? 'bg-gray-100' :
                  percent === 100 ? 'bg-green-400' :
                  percent >= 50 ? 'bg-yellow-400' :
                  percent > 0 ? 'bg-orange-400' :
                  'bg-gray-200'
                }`}>
                  {!isFuture && percent !== null && percent === 100 && (
                    <span className="text-white text-xs">✓</span>
                  )}
                </div>
              </button>
            );
          })}
        </div>
      </motion.div>

      {/* 선택된 날짜 기록 */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-base font-bold text-gray-800">
            {selectedDate === today ? '오늘' : formatDateLabel(new Date(selectedDate))} 기록
          </h2>
          {selectedRecord && (
            <span className="text-sm text-blue-500 font-semibold">
              {selectedRecord.takenCount}/{selectedRecord.totalCount} 복용
            </span>
          )}
        </div>

        {selectedDate > today ? (
          <div className="bg-white rounded-2xl p-8 text-center shadow-sm border border-blue-50">
            <p className="text-4xl mb-2">📅</p>
            <p className="text-sm text-gray-400">아직 오지 않은 날이에요</p>
          </div>
        ) : selectedRecord ? (
          <div className="flex flex-col gap-2.5">
            {selectedRecord.medicines.map((med, idx) => (
              <motion.div
                key={med.id}
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: idx * 0.05 }}
                className="bg-white rounded-2xl p-4 flex items-center gap-3 shadow-sm border border-blue-50"
              >
                <div className={`w-8 h-8 rounded-xl flex items-center justify-center text-sm ${
                  med.taken ? 'bg-blue-50' : 'bg-gray-50'
                }`}>
                  {timeIcons[med.time]}
                </div>
                <div className="flex-1">
                  <p className={`text-sm font-semibold ${med.taken ? 'text-gray-800' : 'text-gray-400'}`}>
                    {med.name}
                  </p>
                  <p className="text-xs text-gray-400 mt-0.5">{med.time}</p>
                </div>
                <div className={`px-3 py-1 rounded-lg text-xs font-semibold ${
                  med.taken ? 'bg-blue-50 text-blue-500' : 'bg-gray-100 text-gray-400'
                }`}>
                  {med.taken ? '복용 완료' : '미복용'}
                </div>
              </motion.div>
            ))}
          </div>
        ) : (
          <div className="bg-white rounded-2xl p-8 text-center shadow-sm border border-blue-50">
            <p className="text-4xl mb-2">💊</p>
            <p className="text-sm text-gray-400">이 날의 기록이 없어요</p>
            <p className="text-xs text-gray-300 mt-1">오늘부터 약 복용을 시작해보세요</p>
          </div>
        )}
      </div>

      {/* 주간 통계 요약 */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
        className="bg-white rounded-2xl p-4 mt-4 mb-2 shadow-sm border border-blue-50"
      >
        <h3 className="text-sm font-bold text-gray-700 mb-3">이번 주 통계</h3>
        <div className="grid grid-cols-3 gap-3">
          <div className="text-center">
            <p className="text-2xl font-bold text-blue-500">
              {weekStats.filter(s => s.percent === 100).length}일
            </p>
            <p className="text-xs text-gray-400 mt-0.5">완벽 복용</p>
          </div>
          <div className="text-center">
            <p className="text-2xl font-bold text-yellow-500">
              {weekStats.filter(s => s.percent !== null && s.percent > 0 && s.percent < 100).length}일
            </p>
            <p className="text-xs text-gray-400 mt-0.5">부분 복용</p>
          </div>
          <div className="text-center">
            <p className="text-2xl font-bold text-gray-400">
              {weekStats.filter(s => s.percent === 0 && s.date <= today).length}일
            </p>
            <p className="text-xs text-gray-400 mt-0.5">미복용</p>
          </div>
        </div>
      </motion.div>
    </div>
  );
}
