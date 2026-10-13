package br.com.gameshelf.users;

// Nunca serializar a entidade AppUser em uma resposta de autenticação.
public record UserView(Long id, String username, String displayName, Role role) {
    public static UserView from(AppUser user) {
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
