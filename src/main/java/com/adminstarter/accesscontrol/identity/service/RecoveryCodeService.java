package com.adminstarter.accesscontrol.identity.service;

import com.adminstarter.accesscontrol.identity.entity.RecoveryCode;
import com.adminstarter.accesscontrol.identity.repository.RecoveryCodeRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recovery codes for two-step sign-in (P-025): ten single-use codes, given when it is turned on and on request, kept
 * only as hashes, as passwords are. Any unused one stands in for a code from the app, once.
 */
@Service
@Transactional
public class RecoveryCodeService {

    static final int COUNT = 10;
    /** Lowercase letters and digits, without the ones easy to mix up: 0, 1, i, l, o */
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final int LENGTH = 10;
    private static final Pattern FORMAT = Pattern.compile("[" + ALPHABET + "]{" + LENGTH + "}");

    private final RecoveryCodeRepository codes;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RecoveryCodeService(RecoveryCodeRepository codes, PasswordEncoder encoder, Clock clock) {
        this.codes = codes;
        this.encoder = encoder;
        this.clock = clock;
    }

    /** Ten new codes, such as "abcde-fghjk", which replace the person's old ones; shown this once */
    public List<String> replace(UUID userId) {
        codes.deleteByUserId(userId);
        var now = clock.instant();
        var made = new ArrayList<String>();
        for (var i = 0; i < COUNT; i++) {
            var code = newCode();
            codes.save(new RecoveryCode(userId, encoder.encode(normalize(code)), now));
            made.add(code.substring(0, LENGTH / 2) + "-" + code.substring(LENGTH / 2));
        }
        return made;
    }

    /**
     * Whether this is one of the person's unused codes, ignoring case, spaces and the dash; it is then used up. A code
     * from the app (six digits) is never one, so it costs no hashing.
     */
    public boolean use(UUID userId, String code) {
        var typed = normalize(code);
        if (!FORMAT.matcher(typed).matches()) {
            return false;
        }
        for (var candidate : codes.findByUserIdAndUsedAtIsNull(userId)) {
            if (encoder.matches(typed, candidate.getCodeHash())) {
                candidate.use(clock.instant());
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public int remaining(UUID userId) {
        return (int) codes.countByUserIdAndUsedAtIsNull(userId);
    }

    /** Two-step sign-in was turned off: the codes go with it */
    public void deleteAll(UUID userId) {
        codes.deleteByUserId(userId);
    }

    private String newCode() {
        var code = new StringBuilder(LENGTH);
        for (var i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    private static String normalize(String code) {
        return code.toLowerCase(Locale.ROOT).replaceAll("[\\s-]", "");
    }
}
