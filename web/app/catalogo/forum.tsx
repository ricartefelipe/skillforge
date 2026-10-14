import type { Doubt } from "../../lib/forum";

export function Forum({
  lessonId,
  doubts,
  openDoubt,
  replyDoubt,
}: {
  lessonId: string;
  doubts: Doubt[] | null;
  openDoubt: (formData: FormData) => void;
  replyDoubt: (formData: FormData) => void;
}) {
  return (
    <section className="tree" aria-label="Fórum">
      <h2>Dúvidas</h2>
      {doubts === null ? <p className="empty">O fórum não responde.</p> : null}
      {doubts !== null && doubts.length === 0 ? <p className="empty">Nenhuma dúvida ainda.</p> : null}
      {doubts?.map((doubt) => (
        <section className="module" key={doubt.id} aria-label={doubt.title}>
          <h3>{doubt.title}</h3>
          <p className="meta">{doubt.author}</p>
          <p>{doubt.body}</p>
          <ul>
            {doubt.replies.map((reply) => (
              <li key={reply.id}>
                <span className="meta">{reply.author}</span>
                <span>{reply.body}</span>
              </li>
            ))}
          </ul>
          <form className="compose" action={replyDoubt} aria-label={`Resposta em ${doubt.title}`}>
            <input type="hidden" name="lessonId" value={lessonId} />
            <input type="hidden" name="doubtId" value={doubt.id} />
            <label>
              Resposta
              <textarea name="body" required />
            </label>
            <button className="quiet" type="submit">Responder</button>
          </form>
        </section>
      ))}
      <form className="compose" action={openDoubt} aria-label="Nova dúvida">
        <input type="hidden" name="lessonId" value={lessonId} />
        <label>
          Título
          <input name="title" required />
        </label>
        <label>
          Dúvida
          <textarea name="body" required />
        </label>
        <button type="submit">Perguntar</button>
      </form>
    </section>
  );
}
