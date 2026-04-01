/**
 * MediAI - AI 상담 화면
 * 디자인: 모던 플랫 헬스케어
 * - AI 약 복용 상담 채팅 UI
 * - 빠른 질문 버튼
 */
import { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Send, Sparkles, Bot } from 'lucide-react';
import { useMed } from '@/contexts/MedContext';

interface Message {
  id: string;
  role: 'ai' | 'user';
  content: string;
  timestamp: Date;
}

const QUICK_QUESTIONS = [
  '오늘 약 복용 현황 알려줘',
  '타이레놀 부작용이 뭐야?',
  '약 복용 시간 추천해줘',
  '약 상호작용 확인해줘',
];

const AI_RESPONSES: Record<string, string> = {
  '오늘 약 복용 현황 알려줘': '오늘 복용 현황을 확인했어요! 현재까지 {taken}개/{total}개를 복용하셨네요. {remaining}개가 남아있어요. 꾸준한 복용이 건강에 도움이 됩니다! 💊',
  '타이레놀 부작용이 뭐야?': '타이레놀(아세트아미노펜)의 주요 부작용으로는 간 손상(과다복용 시), 메스꺼움, 두통 등이 있어요. 권장 용량(성인 기준 1회 500~1000mg, 1일 최대 4000mg)을 초과하지 않도록 주의하세요. 음주 중에는 복용을 피하는 것이 좋습니다.',
  '약 복용 시간 추천해줘': '약 복용 시간 추천드릴게요!\n\n🌅 아침 (식후 30분): 비타민, 철분제\n☀️ 점심 (식후 30분): 소화제, 항생제\n🌙 저녁 (식후 30분): 혈압약, 콜레스테롤약\n🌙 취침 전: 수면 보조제, 일부 항히스타민제\n\n식사와 함께 복용하면 위장 자극을 줄일 수 있어요!',
  '약 상호작용 확인해줘': '현재 등록된 약들의 상호작용을 확인했어요:\n\n✅ 타이레놀 + 비타민 D: 안전\n✅ 타이레놀 + 오메가3: 안전\n⚠️ 주의: 타이레놀은 음주와 함께 복용 시 간 손상 위험이 있어요.\n\n더 자세한 상담은 약사나 의사에게 문의하세요.',
};

function getAIResponse(question: string, taken: number, total: number): string {
  const template = AI_RESPONSES[question];
  if (template) {
    return template
      .replace('{taken}', String(taken))
      .replace('{total}', String(total))
      .replace('{remaining}', String(total - taken));
  }
  return `"${question}"에 대한 답변을 드릴게요. 약 복용과 관련된 구체적인 질문을 해주시면 더 정확한 정보를 제공해드릴 수 있어요. 건강에 관한 중요한 결정은 반드시 의사나 약사와 상담하세요. 💙`;
}

export default function AIPage() {
  const { takenCount, totalCount } = useMed();
  const [messages, setMessages] = useState<Message[]>([
    {
      id: '0',
      role: 'ai',
      content: '안녕하세요! 저는 MediAI 약 복용 도우미예요 💊\n\n약 복용 정보, 부작용, 상호작용 등 궁금한 점을 물어보세요. 아래 빠른 질문 버튼을 눌러도 돼요!',
      timestamp: new Date(),
    }
  ]);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const sendMessage = (text: string) => {
    if (!text.trim()) return;

    const userMsg: Message = {
      id: Date.now().toString(),
      role: 'user',
      content: text,
      timestamp: new Date(),
    };

    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setIsTyping(true);

    setTimeout(() => {
      const aiMsg: Message = {
        id: (Date.now() + 1).toString(),
        role: 'ai',
        content: getAIResponse(text, takenCount, totalCount),
        timestamp: new Date(),
      };
      setMessages(prev => [...prev, aiMsg]);
      setIsTyping(false);
    }, 1000 + Math.random() * 800);
  };

  const formatTime = (date: Date) => {
    return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
  };

  return (
    <div className="flex flex-col" style={{ height: 'calc(100dvh - 64px)' }}>
      {/* 헤더 */}
      <div className="px-4 pt-4 pb-3 flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 blue-gradient-card rounded-xl flex items-center justify-center">
            <Sparkles size={18} className="text-white" />
          </div>
          <div>
            <h1 className="text-lg font-bold text-gray-800">AI 약 도우미</h1>
            <div className="flex items-center gap-1.5">
              <div className="w-2 h-2 bg-green-400 rounded-full animate-pulse" />
              <p className="text-xs text-gray-400">온라인</p>
            </div>
          </div>
        </div>
      </div>

      {/* 메시지 영역 */}
      <div className="flex-1 overflow-y-auto px-4 py-2 flex flex-col gap-3">
        <AnimatePresence>
          {messages.map(msg => (
            <motion.div
              key={msg.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3 }}
              className={`flex gap-2.5 ${msg.role === 'user' ? 'flex-row-reverse' : 'flex-row'}`}
            >
              {msg.role === 'ai' && (
                <div className="w-8 h-8 blue-gradient-card rounded-xl flex items-center justify-center flex-shrink-0 mt-0.5">
                  <Bot size={14} className="text-white" />
                </div>
              )}
              <div className={`flex flex-col gap-1 ${msg.role === 'user' ? 'items-end' : 'items-start'}`}>
                <div className={msg.role === 'ai' ? 'chat-bubble-ai' : 'chat-bubble-user'}>
                  <p className="text-sm leading-relaxed whitespace-pre-line">
                    {msg.content}
                  </p>
                </div>
                <span className="text-xs text-gray-300">{formatTime(msg.timestamp)}</span>
              </div>
            </motion.div>
          ))}
        </AnimatePresence>

        {/* 타이핑 인디케이터 */}
        {isTyping && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="flex gap-2.5"
          >
            <div className="w-8 h-8 blue-gradient-card rounded-xl flex items-center justify-center flex-shrink-0">
              <Bot size={14} className="text-white" />
            </div>
            <div className="chat-bubble-ai flex items-center gap-1">
              <span className="w-2 h-2 bg-blue-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
              <span className="w-2 h-2 bg-blue-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
              <span className="w-2 h-2 bg-blue-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
            </div>
          </motion.div>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* 빠른 질문 */}
      <div className="px-4 py-2 flex-shrink-0">
        <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-hide">
          {QUICK_QUESTIONS.map(q => (
            <button
              key={q}
              onClick={() => sendMessage(q)}
              className="flex-shrink-0 bg-white border border-blue-200 text-blue-500 rounded-xl px-3 py-1.5 text-xs font-medium hover:bg-blue-50 transition-colors"
            >
              {q}
            </button>
          ))}
        </div>
      </div>

      {/* 입력창 */}
      <div className="px-4 pb-4 pt-2 flex-shrink-0">
        <div className="flex gap-2 items-end">
          <div className="flex-1 bg-white border border-blue-100 rounded-2xl px-4 py-3 flex items-center gap-2 shadow-sm">
            <input
              type="text"
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && !e.shiftKey && sendMessage(input)}
              placeholder="약에 대해 궁금한 점을 물어보세요..."
              className="flex-1 text-sm text-gray-700 outline-none bg-transparent placeholder:text-gray-300"
            />
          </div>
          <button
            onClick={() => sendMessage(input)}
            disabled={!input.trim() || isTyping}
            className="w-11 h-11 blue-gradient-card rounded-xl flex items-center justify-center shadow-sm disabled:opacity-40 transition-opacity flex-shrink-0"
          >
            <Send size={16} className="text-white" />
          </button>
        </div>
      </div>
    </div>
  );
}
