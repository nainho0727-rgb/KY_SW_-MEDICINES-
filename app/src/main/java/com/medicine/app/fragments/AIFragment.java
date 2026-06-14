package com.medicine.app.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.medicine.app.BuildConfig;
import com.medicine.app.R;
import com.medicine.app.adapters.ChatAdapter;
import com.medicine.app.models.ChatMessage;
import com.medicine.app.models.Medicine;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static android.content.Context.MODE_PRIVATE;

public class AIFragment extends Fragment {

    private static final String TAG = "AIFragment";
    /** 사용 모델 우선순위. 한도(429)에 막히면 다음 모델(별도 무료 할당량 버킷)로 자동 전환.
     *  2.0 계열은 이 키에서 limit=0 이라 제외. */
    private static final String[] MODELS = {
            "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-flash-latest"
    };
    /** 일시적 503 시 같은 모델 재시도 횟수. */
    private static final int MAX_RETRIES = 2;
    private RecyclerView rvChat;
    private EditText etMessage;
    private View btnSend;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatList = new ArrayList<>();
    private FirebaseFirestore db;
    private String userId;
    private String todayDate;
    
    private Executor executor = Executors.newSingleThreadExecutor();
    /** 직전에 성공한 모델 인덱스. 한도로 막힌 모델을 매번 다시 찌르지 않도록 여기서 시작. */
    private volatile int preferredModelIndex = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = requireActivity().getSharedPreferences("medicine_prefs", MODE_PRIVATE);
        userId = prefs.getString("user_id", "");
        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        rvChat = view.findViewById(R.id.rv_chat);
        etMessage = view.findViewById(R.id.et_message);
        btnSend = view.findViewById(R.id.btn_send);

        chatAdapter = new ChatAdapter(chatList);
        rvChat.setLayoutManager(new LinearLayoutManager(getContext()));
        rvChat.setAdapter(chatAdapter);

        // 초기 메시지
        if (chatList.isEmpty()) {
            addAIMessage("안녕하세요! 저는 MEDICINES 약 복용 도우미예요 📊\n\n약 복용 정보, 부작용, 상호작용 등 궁금한 점을 물어보세요. 아래 빠른 질문 버튼을 누르셔도 되어요!");
        }

        if (userId.isEmpty()) {
            addAIMessage("로그인 정보가 없어 개인화된 답변을 제공하기 어렵습니다. 로그인 후 이용해주세요.");
        }

        btnSend.setOnClickListener(v -> sendMessage());

        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        // 빠른 질문 버튼들 연동
        setupQuickQuestion(view.findViewById(R.id.chip_status), "오늘 약 복용 현황 알려줘");
        setupQuickQuestion(view.findViewById(R.id.chip_next), "다음 약 언제 먹어?");
        setupQuickQuestion(view.findViewById(R.id.chip_missing), "오늘 안 먹은 약 있어?");
        setupQuickQuestion(view.findViewById(R.id.chip_rate), "오늘 복용률 알려줘");
        setupQuickQuestion(view.findViewById(R.id.chip_side_effect), "타이레놀 부작용 알려줘");
        setupQuickQuestion(view.findViewById(R.id.chip_time_recommend), "약 복용 시간 추천해줘");
        setupQuickQuestion(view.findViewById(R.id.chip_cold_caution), "감기약 복용 시 주의사항");
        setupQuickQuestion(view.findViewById(R.id.chip_supple_time), "영양제는 언제 먹는게 좋아?");
        setupQuickQuestion(view.findViewById(R.id.chip_forget), "약을 깜빡했을 때 어떻게 해?");
        setupQuickQuestion(view.findViewById(R.id.chip_meal_diff), "식전 복용과 식후 복용 차이");
        setupQuickQuestion(view.findViewById(R.id.chip_headache_recommend), "두통약 추천해줘");
    }

    private void setupQuickQuestion(View view, String question) {
        if (view != null) {
            view.setOnClickListener(v -> {
                addUserMessage(question);
                processUserRequest(question);
            });
        }
    }

    private void sendMessage() {
        String message = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(message)) return;

        addUserMessage(message);
        etMessage.setText("");
        processUserRequest(message);
    }

    private void addUserMessage(String message) {
        String time = new SimpleDateFormat("HH:mm", Locale.KOREAN).format(new Date());
        chatList.add(new ChatMessage(message, ChatMessage.TYPE_USER, time));
        chatAdapter.notifyItemInserted(chatList.size() - 1);
        rvChat.scrollToPosition(chatList.size() - 1);
    }

    private void addAIMessage(String message) {
        String time = new SimpleDateFormat("HH:mm", Locale.KOREAN).format(new Date());
        chatList.add(new ChatMessage(message, ChatMessage.TYPE_AI, time));
        chatAdapter.notifyItemInserted(chatList.size() - 1);
        rvChat.scrollToPosition(chatList.size() - 1);
    }

    private void addLoadingMessage() {
        ChatMessage loadingMsg = new ChatMessage("", ChatMessage.TYPE_LOADING, "");
        loadingMsg.setLoading(true);
        chatList.add(loadingMsg);
        chatAdapter.notifyItemInserted(chatList.size() - 1);
        rvChat.scrollToPosition(chatList.size() - 1);
    }

    private void removeLoadingMessage() {
        if (!chatList.isEmpty() && chatList.get(chatList.size() - 1).getType() == ChatMessage.TYPE_LOADING) {
            int pos = chatList.size() - 1;
            chatList.remove(pos);
            chatAdapter.notifyItemRemoved(pos);
        }
    }

    private void processUserRequest(String userMessage) {
        if (userId == null || userId.isEmpty()) {
            addAIMessage("로그인 정보가 없어 개인화된 답변을 제공하기 어렵습니다. 로그인 후 이용해주세요.");
            return;
        }

        addLoadingMessage();

        // 복약 현황 관련 질문인지 확인
        if (userMessage.contains("현황") || userMessage.contains("복용률") || 
            userMessage.contains("안 먹은") || userMessage.contains("언제 먹어") ||
            userMessage.contains("복약") || userMessage.contains("약 먹었어")) {
            fetchFirebaseDataAndCallGemini(userMessage);
        } else {
            callGeminiApi(userMessage, "");
        }
    }

    private void fetchFirebaseDataAndCallGemini(String userMessage) {
        db.collection("medicines")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener(medicineSnapshots -> {
                List<Medicine> allMedicines = new ArrayList<>();
                for (QueryDocumentSnapshot doc : medicineSnapshots) {
                    Medicine baseMedicine = doc.toObject(Medicine.class);
                    baseMedicine.setId(doc.getId());
                    
                    List<String> timeSlots = baseMedicine.getTimeSlots();
                    if (timeSlots != null && !timeSlots.isEmpty()) {
                        for (String slot : timeSlots) {
                            Medicine medicineForSlot = new Medicine(baseMedicine);
                            medicineForSlot.setTimeSlots(Collections.singletonList(slot));
                            allMedicines.add(medicineForSlot);
                        }
                    } else {
                        Medicine medicineForSlot = new Medicine(baseMedicine);
                        medicineForSlot.setTimeSlots(Collections.singletonList("morning"));
                        allMedicines.add(medicineForSlot);
                    }
                }

                db.collection("records")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("date", todayDate)
                    .get()
                    .addOnSuccessListener(recordSnapshots -> {
                        for (Medicine m : allMedicines) {
                            for (DocumentSnapshot recordDoc : recordSnapshots) {
                                if (m.getId().equals(recordDoc.getString("medicineId")) &&
                                    m.getTimeSlots().get(0).equals(recordDoc.getString("timeSlot"))) {
                                    m.setTaken(true);
                                    break;
                                }
                            }
                        }
                        
                        // Firebase 데이터를 문자열로 변환하여 프롬프트에 포함
                        String context = buildMedicineContext(allMedicines);
                        callGeminiApi(userMessage, context);
                    })
                    .addOnFailureListener(e -> {
                        removeLoadingMessage();
                        addAIMessage("복용 기록을 불러오는 중 오류가 발생했습니다.");
                    });
            })
            .addOnFailureListener(e -> {
                removeLoadingMessage();
                addAIMessage("약 정보를 불러오는 중 오류가 발생했습니다.");
            });
    }

    private String buildMedicineContext(List<Medicine> medicines) {
        if (medicines.isEmpty()) {
            return "현재 사용자가 등록한 약 정보가 없습니다.";
        }

        StringBuilder sb = new StringBuilder("사용자의 오늘 복약 현황 데이터:\n");
        for (Medicine m : medicines) {
            sb.append("- 약 이름: ").append(m.getName())
              .append(", 복용 시간대: ").append(m.getTimeSlotLabel())
              .append(", 복용 여부: ").append(m.isTaken() ? "복용 완료" : "미복용")
              .append("\n");
        }
        return sb.toString();
    }

    private void callGeminiApi(String userQuery, String context) {
        callGeminiApi(userQuery, context, 0, preferredModelIndex);
    }

    private void callGeminiApi(String userQuery, String context, int attempt, int modelIndex) {
        GenerativeModelFutures model = GenerativeModelFutures.from(new GenerativeModel(
                MODELS[Math.min(modelIndex, MODELS.length - 1)], BuildConfig.GEMINI_API_KEY));

        String systemPrompt = "당신은 'MEDICINES'라는 약 복용 관리 앱의 친절한 AI 도우미입니다. " +
                "사용자의 질문에 답변할 때 다음 지침을 따르세요:\n" +
                "1. 제공된 복약 현황 데이터가 있다면 이를 바탕으로 정확하게 답변하세요.\n" +
                "2. 의학적 조언이 필요한 경우 '이 답변은 참고용이며 전문가와 상담하십시오'라는 취지의 문구를 포함하세요.\n" +
                "3. 답변은 친절하고 정중하게 한국어로 작성하세요.\n" +
                "4. 복약 현황에 대해 답할 때는 구체적인 약 이름과 시간을 언급해주세요.\n\n";

        String fullPrompt = systemPrompt;
        if (!context.isEmpty()) {
            fullPrompt += "현재 상황:\n" + context + "\n\n";
        }
        fullPrompt += "사용자 질문: " + userQuery;

        Content content = new Content.Builder()
                .addText(fullPrompt)
                .build();

        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                preferredModelIndex = modelIndex; // 이 모델이 성공 → 다음부터 여기서 시작
                String text = result.getText();
                runOnUi(() -> {
                    removeLoadingMessage();
                    addAIMessage(TextUtils.isEmpty(text)
                            ? "응답을 받지 못했어요. 잠시 후 다시 시도해주세요." : text);
                });
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.e(TAG, "Gemini API Error (model=" + modelIndex + ", attempt " + attempt + ")", t);
                boolean hasNextModel = modelIndex + 1 < MODELS.length;
                // 1) 한도(429): 같은 모델 재시도는 의미 없으니 다음 모델 버킷으로 즉시 전환
                if (isQuota(t) && hasNextModel) {
                    runOnUi(() -> {
                        if (rvChat != null) rvChat.post(
                                () -> callGeminiApi(userQuery, context, 0, modelIndex + 1));
                    });
                    return;
                }
                // 2) 일시적 과부하(503): 같은 모델 백오프 재시도(로딩 유지)
                if (!isQuota(t) && isRetryable(t) && attempt < MAX_RETRIES) {
                    long delayMs = 1500L * (attempt + 1); // 1.5s → 3s
                    runOnUi(() -> {
                        if (rvChat != null) rvChat.postDelayed(
                                () -> callGeminiApi(userQuery, context, attempt + 1, modelIndex), delayMs);
                    });
                    return;
                }
                // 3) 재시도 소진 → 남은 다른 모델이 있으면 전환
                if (hasNextModel) {
                    runOnUi(() -> {
                        if (rvChat != null) rvChat.post(
                                () -> callGeminiApi(userQuery, context, 0, modelIndex + 1));
                    });
                    return;
                }
                // 4) 모든 모델 실패 → 안내
                runOnUi(() -> {
                    removeLoadingMessage();
                    addAIMessage(friendlyError(t));
                });
            }
        }, executor);
    }

    /** 사용량 한도(429/RESOURCE_EXHAUSTED) 오류인지. */
    private boolean isQuota(Throwable t) {
        String m = (t == null || t.getMessage() == null) ? "" : t.getMessage().toLowerCase(Locale.ROOT);
        return m.contains("429") || m.contains("resource_exhausted") || m.contains("quota");
    }

    /** Fragment 가 화면에 붙어 있을 때만 UI 작업 실행(detach 중 크래시 방지). */
    private void runOnUi(Runnable r) {
        if (!isAdded()) return;
        androidx.fragment.app.FragmentActivity act = getActivity();
        if (act != null) act.runOnUiThread(r);
    }

    /**
     * 서버 과부하/일시적 오류면 재시도 대상.
     * SDK 가 503 응답을 제대로 파싱 못 해 MissingFieldException 으로 던지는 케이스도 포함.
     */
    private boolean isRetryable(Throwable t) {
        String m = (t == null || t.getMessage() == null) ? "" : t.getMessage().toLowerCase(Locale.ROOT);
        return m.contains("503") || m.contains("unavailable") || m.contains("high demand")
                || m.contains("overload") || m.contains("500") || m.contains("internal")
                || m.contains("timeout") || m.contains("deadline")
                || m.contains("missingfield") || m.contains("unexpected response");
    }

    /** 사용자에게 보여줄 깔끔한 안내 문구(원시 JSON/스택트레이스 노출 방지). */
    private String friendlyError(Throwable t) {
        String m = (t == null || t.getMessage() == null) ? "" : t.getMessage().toLowerCase(Locale.ROOT);
        if (m.contains("429") || m.contains("resource_exhausted") || m.contains("quota")) {
            return "오늘 무료 AI 사용량 한도에 도달했어요. 잠시 후 다시 시도해주세요.";
        }
        if (m.contains("503") || m.contains("unavailable") || m.contains("high demand")
                || m.contains("overload") || m.contains("missingfield")) {
            return "AI 서버가 잠시 혼잡해요(일시적 과부하). 잠시 후 다시 시도해주세요. 🙏";
        }
        if (m.contains("unable to resolve host") || m.contains("network")
                || m.contains("timeout") || m.contains("deadline")) {
            return "네트워크 연결이 불안정해요. 인터넷 상태를 확인하고 다시 시도해주세요.";
        }
        return "죄송합니다. 답변을 가져오지 못했어요. 잠시 후 다시 시도해주세요.";
    }
}
