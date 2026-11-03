package com.familyalbum.google;

import com.familyalbum.config.AppProperties;
import com.familyalbum.domain.GoogleAccount;
import com.familyalbum.repository.AppUserRepository;
import com.familyalbum.repository.GoogleAccountRepository;
import com.familyalbum.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가족 구성원 개인의 구글 계정 연결 상태 관리 */
@Service
public class GoogleConnectionService {

    private final GoogleAccountRepository accounts;
    private final AppUserRepository users;
    private final GoogleOAuthClient oauth;
    private final TokenCipher cipher;
    private final AppProperties props;

    public GoogleConnectionService(GoogleAccountRepository accounts, AppUserRepository users,
                                   GoogleOAuthClient oauth, TokenCipher cipher, AppProperties props) {
        this.accounts = accounts;
        this.users = users;
        this.oauth = oauth;
        this.cipher = cipher;
        this.props = props;
    }

    public boolean enabled() {
        return props.google().enabled();
    }

    @Transactional(readOnly = true)
    public boolean connected(Long userId) {
        return accounts.findByUserId(userId).isPresent();
    }

    @Transactional
    public void connect(Long userId, String code) {
        GoogleOAuthClient.Tokens tokens = oauth.exchangeCode(code);
        if (tokens.refreshToken() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "구글에서 갱신 토큰을 받지 못했어요. 구글 계정의 '서드 파티 앱 액세스'에서 이 앱을 제거한 뒤 다시 연결해 주세요.");
        }
        String enc = cipher.encrypt(tokens.refreshToken());
        GoogleAccount account = accounts.findByUserId(userId).orElse(null);
        if (account == null) {
            accounts.save(new GoogleAccount(users.getReferenceById(userId), enc));
        } else {
            account.replaceToken(enc);
        }
    }

    @Transactional
    public void disconnect(Long userId) {
        accounts.findByUserId(userId).ifPresent(a -> {
            try {
                oauth.revoke(cipher.decrypt(a.getRefreshTokenEnc()));
            } catch (RuntimeException ignored) {
                // 복호화 실패 등은 무시하고 로컬 연결만 해제
            }
            accounts.delete(a);
        });
    }

    /**
     * 사용 직전에 access token 을 새로 받는다(저장하지 않음).
     * 토큰이 폐기/만료(개발용 'Testing' 상태 앱은 7일)됐다면 연결 정보를 지우고 재연결을 요구한다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public String accessToken(Long userId) {
        GoogleAccount account = accounts.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "구글 포토가 연결되어 있지 않아요."));
        try {
            return oauth.refreshAccessToken(cipher.decrypt(account.getRefreshTokenEnc()));
        } catch (GoogleAuthException | IllegalStateException e) {
            accounts.delete(account);
            throw new ApiException(HttpStatus.CONFLICT, "구글 연결이 만료됐어요. 구글 포토 메뉴에서 다시 연결해 주세요.");
        }
    }
}
