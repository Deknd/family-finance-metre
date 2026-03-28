package com.deknd.familyfinancemetre.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Сущность члена семьи, для которого можно запускать сбор финансовых данных.
 */
@Getter
@Setter
@Entity
@Table(name = "family_members")
public class FamilyMemberEntity extends AbstractAuditableEntity {

	/**
	 * Семья, к которой относится пользователь.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_id", nullable = false)
	private FamilyEntity family;

	/**
	 * Имя пользователя.
	 */
	@Column(name = "first_name", nullable = false)
	private String firstName;

	/**
	 * Фамилия пользователя.
	 */
	@Column(name = "last_name")
	private String lastName;

	/**
	 * Короткое отображаемое имя для интерфейсов и интеграций.
	 */
	@Column(name = "display_name")
	private String displayName;

	/**
	 * Telegram chat id для запуска и продолжения диалога.
	 */
	@Column(name = "telegram_chat_id", length = 64)
	private String telegramChatId;

	/**
	 * Telegram username пользователя.
	 */
	@Column(name = "telegram_username")
	private String telegramUsername;

	/**
	 * Признак участия пользователя в текущем MVP-сценарии.
	 */
	@Column(name = "is_active", nullable = false)
	private Boolean active;
}
