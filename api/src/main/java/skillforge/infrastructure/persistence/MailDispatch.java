package skillforge.infrastructure.persistence;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import skillforge.infrastructure.config.SkillforgeProperties;

@Component
class MailDispatch {

    private final JdbcTemplate jdbc;

    private final Schema schema;

    private final SkillforgeProperties properties;

    MailDispatch(JdbcTemplate jdbc, Schema schema, SkillforgeProperties properties) {
        this.jdbc = jdbc;
        this.schema = schema;
        this.properties = properties;
    }

    void offer(String account, String body) {
        schema.ensure();
        List<String> emails = jdbc.query(
                "SELECT email FROM account_mail WHERE account = ?",
                (rs, row) -> rs.getString("email"),
                account);
        if (emails.isEmpty()) {
            return;
        }
        String email = emails.get(0);
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO mail_outbox (id, account, email, body, created_at) VALUES (?, ?, ?, ?, now())",
                id,
                account,
                email,
                body);
        if (properties.getMailHost() == null || properties.getMailHost().isBlank()) {
            return;
        }
        try {
            send(email, body);
            jdbc.update("UPDATE mail_outbox SET sent_at = now() WHERE id = ?", id);
        }
        catch (Exception ex) {
            return;
        }
    }

    private void send(String email, String body) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", properties.getMailHost());
        props.put("mail.smtp.port", Integer.toString(properties.getMailPort()));
        props.put("mail.smtp.auth", Boolean.toString(!properties.getMailUsername().isBlank()));
        props.put("mail.smtp.starttls.enable", "true");
        Session session = Session.getInstance(props);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(properties.getMailFrom()));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
        message.setSubject("Skillforge");
        message.setText(body, "UTF-8");
        if (properties.getMailUsername().isBlank()) {
            Transport.send(message);
            return;
        }
        Transport transport = session.getTransport("smtp");
        transport.connect(properties.getMailHost(), properties.getMailUsername(), properties.getMailPassword());
        transport.sendMessage(message, message.getAllRecipients());
        transport.close();
    }

}
