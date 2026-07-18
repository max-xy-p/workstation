package com.mike.workstation.mapper;

import com.mike.workstation.domain.Category;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
/**
 * 分类表数据访问接口。
 */
public interface CategoryMapper {
    /**
     * 查询全部分类。
     */
    @Select("SELECT id, name, description, created_at, updated_at FROM categories ORDER BY name")
    List<Category> list();

    /**
     * 根据主键查询分类。
     */
    @Select("SELECT id, name, description, created_at, updated_at FROM categories WHERE id = #{id}")
    Category findById(Long id);

    /**
     * 插入分类，并回填自增主键到 category.id。
     */
    @Insert("""
            INSERT INTO categories (name, description, created_at, updated_at)
            VALUES (#{name}, #{description}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Category category);

    /**
     * 更新分类名称和描述。
     */
    @Update("""
            UPDATE categories
            SET name = #{name}, description = #{description}, updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(Category category);

    /**
     * 根据主键删除分类。
     */
    @Delete("DELETE FROM categories WHERE id = #{id}")
    int delete(Long id);
}
