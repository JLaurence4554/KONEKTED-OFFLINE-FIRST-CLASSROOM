package com.malapay.classroom.ui.quiz.teacher;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.malapay.classroom.R;
import com.malapay.classroom.data.local.entity.Question;
import com.malapay.classroom.data.repository.QuizRepository;
import com.malapay.classroom.data.repository.RepositoryCallback;
import com.malapay.classroom.databinding.ActivityCreateEditQuestionBinding;
import com.malapay.classroom.util.ValidationUtil;

public class CreateEditQuestionActivity extends AppCompatActivity {

    public static final String EXTRA_QUIZ_ID = "extra_quiz_id";
    public static final String EXTRA_QUESTION_ID = "extra_question_id";
    private static final long NO_ID = -1L;

    private static final String[] QUESTION_TYPES = {
            Question.TYPE_MULTIPLE_CHOICE,
            Question.TYPE_IDENTIFICATION,
            Question.TYPE_ESSAY
    };
    private static final String[] QUESTION_TYPE_LABELS = {
            "Multiple Choice",
            "Identification",
            "Essay"
    };

    private ActivityCreateEditQuestionBinding binding;
    private QuizRepository quizRepository;
    private long quizId;
    private boolean isEditMode;
    private Question existingQuestion;
    private String selectedType = Question.TYPE_MULTIPLE_CHOICE;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateEditQuestionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        quizRepository = new QuizRepository(this);

        binding.appHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.appHeader.btnHeaderBack.setOnClickListener(v -> finish());

        quizId = getIntent().getLongExtra(EXTRA_QUIZ_ID, NO_ID);
        long questionId = getIntent().getLongExtra(EXTRA_QUESTION_ID, NO_ID);
        isEditMode = questionId != NO_ID;

        binding.appHeader.txtHeaderTitle.setText(isEditMode ? R.string.edit_question_title : R.string.create_question_title);

        // Setup type spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, QUESTION_TYPE_LABELS);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerQuestionType.setAdapter(spinnerAdapter);
        binding.spinnerQuestionType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedType = QUESTION_TYPES[position];
                updateVisibleCards();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        binding.btnSave.setOnClickListener(v -> saveQuestion());

        if (isEditMode) {
            loadQuestion(questionId);
        }
    }

    private void loadQuestion(long questionId) {
        quizRepository.getQuestionById(questionId, new RepositoryCallback<Question>() {
            @Override
            public void onSuccess(Question result) {
                existingQuestion = result;
                binding.etQuestionText.setText(result.getQuestionText());

                // Set spinner to correct type
                for (int i = 0; i < QUESTION_TYPES.length; i++) {
                    if (QUESTION_TYPES[i].equals(result.getQuestionType())) {
                        binding.spinnerQuestionType.setSelection(i);
                        selectedType = QUESTION_TYPES[i];
                        break;
                    }
                }

                updateVisibleCards();

                if (result.isMultipleChoice()) {
                    binding.etChoiceA.setText(result.getChoiceA());
                    binding.etChoiceB.setText(result.getChoiceB());
                    binding.etChoiceC.setText(result.getChoiceC());
                    binding.etChoiceD.setText(result.getChoiceD());
                    selectRadioButton(result.getCorrectAnswer());
                } else if (result.isIdentification()) {
                    binding.etAnswerText.setText(result.getAnswerText());
                } else {
                    binding.etModelAnswer.setText(result.getAnswerText());
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(CreateEditQuestionActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
    private void updateVisibleCards() {
        boolean isMcq = Question.TYPE_MULTIPLE_CHOICE.equals(selectedType);
        boolean isId = Question.TYPE_IDENTIFICATION.equals(selectedType);
        binding.cardMultipleChoice.setVisibility(isMcq ? View.VISIBLE : View.GONE);
        binding.cardIdentification.setVisibility(isId ? View.VISIBLE : View.GONE);
        binding.cardEssay.setVisibility(!isMcq && !isId ? View.VISIBLE : View.GONE);
    }

    private void selectRadioButton(String answer) {
        if ("B".equals(answer)) binding.radioB.setChecked(true);
        else if ("C".equals(answer)) binding.radioC.setChecked(true);
        else if ("D".equals(answer)) binding.radioD.setChecked(true);
        else binding.radioA.setChecked(true);
    }

    private String selectedAnswer() {
        if (binding.radioB.isChecked()) return "B";
        if (binding.radioC.isChecked()) return "C";
        if (binding.radioD.isChecked()) return "D";
        return "A";
    }

    private void saveQuestion() {
        String questionText = textOf(binding.etQuestionText);
        if (questionText.isEmpty()) {
            binding.tilQuestionText.setError(getString(R.string.error_field_required));
            return;
        }
        setLoading(true);

        if (Question.TYPE_IDENTIFICATION.equals(selectedType)) {
            String answerText = textOf(binding.etAnswerText);
            if (answerText.isEmpty()) {
                binding.tilAnswerText.setError(getString(R.string.error_identification_answer_required));
                setLoading(false);
                return;
            }
            if (isEditMode && existingQuestion != null) {
                existingQuestion.setQuestionText(questionText);
                existingQuestion.setQuestionType(selectedType);
                existingQuestion.setChoiceA(""); existingQuestion.setChoiceB("");
                existingQuestion.setChoiceC(""); existingQuestion.setChoiceD("");
                existingQuestion.setCorrectAnswer(""); existingQuestion.setAnswerText(answerText);
                quizRepository.updateQuestion(existingQuestion, new RepositoryCallback<Void>() {
                    public void onSuccess(Void r) { setLoading(false); finish(); }
                    public void onError(String m) { setLoading(false); Toast.makeText(CreateEditQuestionActivity.this, m, Toast.LENGTH_SHORT).show(); }
                });
            } else {
                quizRepository.addIdentificationQuestion(quizId, questionText, answerText, new RepositoryCallback<Question>() {
                    public void onSuccess(Question r) { setLoading(false); finish(); }
                    public void onError(String m) { setLoading(false); Toast.makeText(CreateEditQuestionActivity.this, m, Toast.LENGTH_SHORT).show(); }
                });
            }
        } else if (Question.TYPE_ESSAY.equals(selectedType)) {
            String modelAnswer = textOf(binding.etModelAnswer);
            if (isEditMode && existingQuestion != null) {
                existingQuestion.setQuestionText(questionText);
                existingQuestion.setQuestionType(selectedType);
                existingQuestion.setChoiceA(""); existingQuestion.setChoiceB("");
                existingQuestion.setChoiceC(""); existingQuestion.setChoiceD("");
                existingQuestion.setCorrectAnswer(""); existingQuestion.setAnswerText(modelAnswer.isEmpty() ? null : modelAnswer);
                quizRepository.updateQuestion(existingQuestion, new RepositoryCallback<Void>() {
                    public void onSuccess(Void r) { setLoading(false); finish(); }
                    public void onError(String m) { setLoading(false); Toast.makeText(CreateEditQuestionActivity.this, m, Toast.LENGTH_SHORT).show(); }
                });
            } else {
                quizRepository.addEssayQuestion(quizId, questionText, modelAnswer.isEmpty() ? null : modelAnswer, new RepositoryCallback<Question>() {
                    public void onSuccess(Question r) { setLoading(false); finish(); }
                    public void onError(String m) { setLoading(false); Toast.makeText(CreateEditQuestionActivity.this, m, Toast.LENGTH_SHORT).show(); }
                });
            }
        } else {
            // Multiple Choice
            String choiceA = textOf(binding.etChoiceA);
            String choiceB = textOf(binding.etChoiceB);
            String choiceC = textOf(binding.etChoiceC);
            String choiceD = textOf(binding.etChoiceD);
            String correctAnswer = selectedAnswer();
            if (choiceA.isEmpty() || choiceB.isEmpty()) {
                Toast.makeText(this, R.string.error_field_required, Toast.LENGTH_SHORT).show();
                setLoading(false);
                return;
            }
            if (isEditMode && existingQuestion != null) {
                existingQuestion.setQuestionText(questionText);
                existingQuestion.setQuestionType(selectedType);
                existingQuestion.setChoiceA(choiceA); existingQuestion.setChoiceB(choiceB);
                existingQuestion.setChoiceC(choiceC); existingQuestion.setChoiceD(choiceD);
                existingQuestion.setCorrectAnswer(correctAnswer); existingQuestion.setAnswerText(null);
                quizRepository.updateQuestion(existingQuestion, new RepositoryCallback<Void>() {
                    public void onSuccess(Void r) { setLoading(false); finish(); }
                    public void onError(String m) { setLoading(false); Toast.makeText(CreateEditQuestionActivity.this, m, Toast.LENGTH_SHORT).show(); }
                });
            } else {
                quizRepository.addQuestion(quizId, questionText, choiceA, choiceB, choiceC, choiceD, correctAnswer, new RepositoryCallback<Question>() {
                    public void onSuccess(Question r) { setLoading(false); finish(); }
                    public void onError(String m) { setLoading(false); Toast.makeText(CreateEditQuestionActivity.this, m, Toast.LENGTH_SHORT).show(); }
                });
            }
        }
    }

    private String textOf(com.google.android.material.textfield.TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnSave.setEnabled(!loading);
    }
}