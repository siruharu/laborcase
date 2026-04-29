-- V2: add 조문가지번호 ("jo branch") to article.
--
-- Real legal text uses entries like 제43조의2, 제43조의3, … that share the
-- same 조문번호 (43) but have distinct sub-numbers. The DRF XML records these
-- as <조문가지번호>2</조문가지번호> inside the same <조문단위> tree. Our Task 2
-- schema only had (jo, hang, ho, mok), so 제43조 and 제43조의2 collided on the
-- UNIQUE index. Task 4 parser tests caught this on the 근로기준법 fixture
-- where 제43조 has 7 sub-articles.
--
-- jo_branch is smallint and NULL for the primary article (제N조). A non-null
-- value corresponds to 제N조의K, stored as the plain integer K.

ALTER TABLE article ADD COLUMN jo_branch smallint;

-- Drop the old unique constraint and recreate with jo_branch included.
-- We look it up by name via the column list to stay portable across
-- future ALTERs.
ALTER TABLE article DROP CONSTRAINT article_law_version_id_jo_hang_ho_mok_key;
ALTER TABLE article
    ADD CONSTRAINT article_locator_uq
    UNIQUE NULLS NOT DISTINCT (law_version_id, jo, jo_branch, hang, ho, mok);

-- Rebuild the covering index to match the new locator order.
DROP INDEX IF EXISTS article_law_version_jo_idx;
CREATE INDEX article_law_version_jo_idx ON article (law_version_id, jo, jo_branch);

COMMENT ON COLUMN article.jo_branch IS
    '조문가지번호 (sub-article). NULL for the primary 제N조; K for 제N조의K.';
