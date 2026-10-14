"use client";

import { useState } from "react";

type Choice = { label: string; correct: boolean };
type Question = { prompt: string; choices: Choice[] };

const blank = (): Question => ({
  prompt: "",
  choices: [
    { label: "", correct: true },
    { label: "", correct: false },
  ],
});

export function ExamForm({
  cohortId,
  action,
  returnTo,
}: {
  cohortId: string;
  action: (formData: FormData) => void;
  returnTo?: string;
}) {
  const [title, setTitle] = useState("");
  const [questions, setQuestions] = useState<Question[]>([blank()]);
  const spec = JSON.stringify({ title, questions });

  return (
    <form className="compose" action={action} aria-label="Nova prova">
      <input type="hidden" name="cohortId" value={cohortId} />
      {returnTo ? <input type="hidden" name="returnTo" value={returnTo} /> : null}
      <input type="hidden" name="spec" value={spec} />
      <label>
        Título da prova
        <input value={title} onChange={(event) => setTitle(event.target.value)} required />
      </label>
      {questions.map((question, questionIndex) => (
        <fieldset key={questionIndex}>
          <legend>Questão {questionIndex + 1}</legend>
          <label>
            Enunciado
            <textarea
              value={question.prompt}
              required
              onChange={(event) => updateQuestion(questionIndex, { ...question, prompt: event.target.value })}
            />
          </label>
          {question.choices.map((choice, choiceIndex) => (
            <label key={choiceIndex}>
              Alternativa {choiceIndex + 1}
              <input
                value={choice.label}
                required
                onChange={(event) => replaceChoice(questionIndex, choiceIndex, event.target.value)}
              />
              <input
                type="radio"
                name={`correct-${questionIndex}`}
                checked={choice.correct}
                onChange={() => markCorrect(questionIndex, choiceIndex)}
              />{" "}
              Correta
            </label>
          ))}
          <button type="button" className="quiet" onClick={() => addChoice(questionIndex)}>
            Outra alternativa
          </button>
        </fieldset>
      ))}
      <button type="button" className="quiet" onClick={() => setQuestions([...questions, blank()])}>
        Outra questão
      </button>
      <button type="submit">Publicar prova</button>
    </form>
  );

  function updateQuestion(index: number, next: Question) {
    setQuestions(questions.map((question, questionIndex) => (questionIndex === index ? next : question)));
  }

  function replaceChoice(questionIndex: number, choiceIndex: number, label: string) {
    const question = questions[questionIndex];
    if (!question) {
      return;
    }
    updateQuestion(questionIndex, {
      ...question,
      choices: question.choices.map((choice, index) => (index === choiceIndex ? { ...choice, label } : choice)),
    });
  }

  function markCorrect(questionIndex: number, choiceIndex: number) {
    const question = questions[questionIndex];
    if (!question) {
      return;
    }
    updateQuestion(questionIndex, {
      ...question,
      choices: question.choices.map((choice, index) => ({ ...choice, correct: index === choiceIndex })),
    });
  }

  function addChoice(questionIndex: number) {
    const question = questions[questionIndex];
    if (!question) {
      return;
    }
    updateQuestion(questionIndex, {
      ...question,
      choices: [...question.choices, { label: "", correct: false }],
    });
  }
}
