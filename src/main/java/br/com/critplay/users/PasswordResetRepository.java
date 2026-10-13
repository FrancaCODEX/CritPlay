package br.com.critplay.users;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordResetToken,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash=:hash")
    Optional<PasswordResetToken> findLocked(@Param("hash") String hash);
    Optional<PasswordResetToken> findTopByUserIdOrderByIdDesc(Long userId);
    @Modifying @Query("update PasswordResetToken t set t.used=true where t.userId=:userId")
    void revokeUserTokens(@Param("userId") Long userId);
}
