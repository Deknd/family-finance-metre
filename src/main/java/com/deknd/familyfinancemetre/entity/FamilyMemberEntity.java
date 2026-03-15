package com.deknd.familyfinancemetre.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "family_members")
public class FamilyMemberEntity extends AbstractAuditableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "family_id", nullable = false)
	private FamilyEntity family;

	@Column(name = "first_name", nullable = false)
	private String firstName;

	@Column(name = "last_name")
	private String lastName;

	@Column(name = "display_name")
	private String displayName;

	@Column(name = "telegram_chat_id", length = 64)
	private String telegramChatId;

	@Column(name = "telegram_username")
	private String telegramUsername;

	@Column(name = "is_active", nullable = false)
	private Boolean active;
}
