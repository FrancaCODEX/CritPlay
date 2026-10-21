package br.com.critplay.social;

import br.com.critplay.users.AppUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Public identity allowlist, shared by search, connections and public profiles. */
public record PersonIdentity(long id, String username, String displayName, String avatarUrl,
                             ProfileVisibility visibility) {
    public static PersonIdentity from(AppUser user) {
        return new PersonIdentity(user.getId(), user.getUsername(), user.getDisplayName(),
            user.getAvatarUrl(), user.getVisibility());
    }

    static PersonIdentity read(ResultSet row) throws SQLException {
        return new PersonIdentity(row.getLong("id"), row.getString("username"),
            row.getString("display_name"), row.getString("avatar_url"),
            ProfileVisibility.valueOf(row.getString("visibility")));
    }

    public Map<String, Object> toMap() {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", id);
        result.put("username", username);
        result.put("displayName", displayName);
        result.put("avatarUrl", avatarUrl);
        result.put("visibility", visibility);
        return result;
    }
}
