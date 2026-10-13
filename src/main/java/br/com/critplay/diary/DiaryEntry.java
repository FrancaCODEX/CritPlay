package br.com.critplay.diary;

import jakarta.persistence.*;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "diary_entries", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "game_id"}))
public class DiaryEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long gameId;
    private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PlayStatus status;
    private Integer rating;
    @Column(length = 2000) private String review;
    private boolean favorite;
    @Column(nullable=false) private boolean wishlist;
    private Instant updatedAt;
    private java.time.LocalDate startDate;
    private java.time.LocalDate finishDate;
    @Column(nullable=false) private boolean reviewHidden;
    @Version @Column(nullable=false) private long rowVersion;
    protected DiaryEntry() {}
    public DiaryEntry(Long userId, Long gameId, DiaryRequest request) {
        this.userId = userId; this.gameId = gameId; update(request);
    }
    public void update(DiaryRequest request) {
        this.status = request.status(); this.rating = request.rating();
        this.review = request.review() == null ? "" : request.review().strip();
        this.favorite = request.favorite(); this.updatedAt = Instant.now();
        this.startDate=request.startDate();this.finishDate=request.finishDate();
        this.wishlist=request.wishlist()!=null?request.wishlist():request.status()==PlayStatus.WANT_TO_PLAY;
    }
    public java.time.LocalDate getStartDate(){return startDate;}
    public java.time.LocalDate getFinishDate(){return finishDate;}
    public boolean isReviewHidden(){return reviewHidden;}
    public void moderate(boolean hidden){reviewHidden=hidden;}
    public void assignOwner(Long userId) { this.userId = userId; }
    @JsonIgnore public Long getUserId() { return userId; }
    public Long getGameId() { return gameId; }
    public PlayStatus getStatus() { return status; }
    public Integer getRating() { return rating; }
    public String getReview() { return review; }
    public boolean isFavorite() { return favorite; }
    public boolean isWishlist() {return wishlist;}
    public Instant getUpdatedAt() { return updatedAt; }
}
