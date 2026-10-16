package br.com.critplay.users;

// Nunca serializar a entidade AppUser em uma resposta de autenticação.
public record UserView(Long id, String username, String displayName, Role role, String avatarUrl) {
    public static UserView from(AppUser user) {
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.getAvatarUrl());
    }
}
