/**
 * MediAI - 약 복용 데이터 컨텍스트
 * 모던 플랫 헬스케어 디자인 철학
 * 약 목록, 복용 상태, 기록 관리
 */
import React, { createContext, useContext, useState, useCallback } from 'react';

export type TimeOfDay = '아침' | '점심' | '저녁' | '취침 전';

export interface Medicine {
  id: string;
  name: string;
  time: TimeOfDay;
  dosage: string;
  color: string;
  icon: string;
  taken: boolean;
  takenAt?: string;
}

export interface MedRecord {
  date: string;
  medicines: { id: string; name: string; time: TimeOfDay; taken: boolean; takenAt?: string }[];
  totalCount: number;
  takenCount: number;
}

const TIME_ICONS: Record<TimeOfDay, string> = {
  '아침': '🌅',
  '점심': '☀️',
  '저녁': '🌙',
  '취침 전': '🌙',
};

const INITIAL_MEDICINES: Medicine[] = [
  { id: '1', name: '타이레놀', time: '아침', dosage: '500mg', color: '#FF6B6B', icon: '💊', taken: false },
  { id: '2', name: '비타민 D', time: '아침', dosage: '1000IU', color: '#FFD93D', icon: '🌟', taken: false },
  { id: '3', name: '오메가3', time: '점심', dosage: '1000mg', color: '#6BCB77', icon: '🐟', taken: false },
  { id: '4', name: '타이레놀', time: '저녁', dosage: '500mg', color: '#FF6B6B', icon: '💊', taken: false },
  { id: '5', name: '오메가3', time: '취침 전', dosage: '1000mg', color: '#6BCB77', icon: '🐟', taken: false },
];

// 지난 7일 기록 생성
function generatePastRecords(): MedRecord[] {
  const records: MedRecord[] = [];
  const today = new Date();
  
  for (let i = 1; i <= 7; i++) {
    const date = new Date(today);
    date.setDate(today.getDate() - i);
    const dateStr = date.toISOString().split('T')[0];
    const takenCount = Math.floor(Math.random() * 6);
    
    records.push({
      date: dateStr,
      medicines: INITIAL_MEDICINES.map(m => ({
        id: m.id,
        name: m.name,
        time: m.time,
        taken: Math.random() > 0.4,
        takenAt: Math.random() > 0.4 ? `${dateStr}T0${Math.floor(Math.random() * 9)}:${Math.floor(Math.random() * 60).toString().padStart(2, '0')}:00` : undefined,
      })),
      totalCount: 5,
      takenCount,
    });
  }
  
  return records;
}

interface MedContextType {
  medicines: Medicine[];
  records: MedRecord[];
  todayDate: string;
  takeMedicine: (id: string) => void;
  untakeMedicine: (id: string) => void;
  addMedicine: (med: Omit<Medicine, 'id' | 'taken'>) => void;
  removeMedicine: (id: string) => void;
  takenCount: number;
  totalCount: number;
  nextMedicine: Medicine | null;
  timeIcons: Record<TimeOfDay, string>;
}

const MedContext = createContext<MedContextType | null>(null);

export function MedProvider({ children }: { children: React.ReactNode }) {
  const [medicines, setMedicines] = useState<Medicine[]>(INITIAL_MEDICINES);
  const [records] = useState<MedRecord[]>(generatePastRecords());
  
  const today = new Date();
  const todayDate = `${today.getMonth() + 1}월 ${today.getDate()}일`;

  const takeMedicine = useCallback((id: string) => {
    setMedicines(prev => prev.map(m =>
      m.id === id ? { ...m, taken: true, takenAt: new Date().toISOString() } : m
    ));
  }, []);

  const untakeMedicine = useCallback((id: string) => {
    setMedicines(prev => prev.map(m =>
      m.id === id ? { ...m, taken: false, takenAt: undefined } : m
    ));
  }, []);

  const addMedicine = useCallback((med: Omit<Medicine, 'id' | 'taken'>) => {
    const newMed: Medicine = {
      ...med,
      id: Date.now().toString(),
      taken: false,
    };
    setMedicines(prev => [...prev, newMed]);
  }, []);

  const removeMedicine = useCallback((id: string) => {
    setMedicines(prev => prev.filter(m => m.id !== id));
  }, []);

  const takenCount = medicines.filter(m => m.taken).length;
  const totalCount = medicines.length;
  const nextMedicine = medicines.find(m => !m.taken) || null;

  return (
    <MedContext.Provider value={{
      medicines,
      records,
      todayDate,
      takeMedicine,
      untakeMedicine,
      addMedicine,
      removeMedicine,
      takenCount,
      totalCount,
      nextMedicine,
      timeIcons: TIME_ICONS,
    }}>
      {children}
    </MedContext.Provider>
  );
}

export function useMed() {
  const ctx = useContext(MedContext);
  if (!ctx) throw new Error('useMed must be used within MedProvider');
  return ctx;
}
