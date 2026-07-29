package com.foodmind.foodmindbackend.search.infrastructure.persistence;

import com.foodmind.foodmindbackend.search.application.port.AuthorisedSearchQuery;
import com.foodmind.foodmindbackend.search.domain.SearchCursor;
import com.foodmind.foodmindbackend.search.domain.SearchDocument;
import com.foodmind.foodmindbackend.search.domain.SearchPage;
import com.foodmind.foodmindbackend.search.domain.SearchSourceType;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 30/07/2026 02:01 am
 */

@Repository
public class JdbcAuthorisedSearchQuery implements AuthorisedSearchQuery {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuthorisedSearchQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SearchPage search(UUID actorUserId, String query, Set<SearchSourceType> sourceTypes, int pageSize, SearchCursor after) {
        List<SearchDocument> rows = jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    SELECT *
                    FROM public.foodmind_search_documents_for_user(
                        ?, ?, ?::varchar(20)[], ?, ?, ?, ?, ?
                    )
                    """);
            Array sourceTypeArray = connection.createArrayOf("varchar", sourceTypes(sourceTypes));
            statement.setObject(1, actorUserId);
            statement.setString(2, query);
            statement.setArray(3, sourceTypeArray);
            statement.setInt(4, pageSize);
            statement.setBigDecimal(5, after == null ? null : after.relevance());
            statement.setObject(6, after == null ? null : after.sortAt());
            statement.setString(7, after == null ? null : after.sourceType().name());
            statement.setObject(8, after == null ? null : after.sourceId());
            return statement;
        }, this::searchRow);
        List<SearchDocument> items = pageItems(rows, pageSize);
        String nextCursor = rows.size() > pageSize ? searchCursor(items.get(items.size() - 1)) : null;
        return new SearchPage(items, nextCursor);
    }

    private Object[] sourceTypes(Set<SearchSourceType> sourceTypes) {
        return sourceTypes.stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(SearchSourceType::name)
                .toArray();
    }

    private List<SearchDocument> pageItems(List<SearchDocument> rows, int pageSize) {
        if (rows.size() <= pageSize) {
            return rows;
        }
        return new ArrayList<>(rows.subList(0, pageSize));
    }

    private String searchCursor(SearchDocument document) {
        return new SearchCursor(document.relevance(), document.sortAt(), document.sourceType(), document.sourceId()).encode();
    }

    private SearchDocument searchRow(ResultSet rs, int rowNum) throws SQLException {
        return new SearchDocument(
                SearchSourceType.valueOf(rs.getString("source_type")),
                rs.getObject("source_id", UUID.class),
                rs.getObject("owner_user_id", UUID.class),
                rs.getObject("group_id", UUID.class),
                rs.getString("visibility"),
                rs.getString("title"),
                rs.getString("subtitle"),
                rs.getString("body_excerpt"),
                null,
                rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getObject("sort_at", OffsetDateTime.class),
                rs.getBigDecimal("relevance"));
    }
}
