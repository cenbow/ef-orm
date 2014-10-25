package jef.database.meta;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import jef.database.DbCfg;
import jef.database.DbUtils;
import jef.database.Field;
import jef.database.IQueryableEntity;
import jef.database.annotation.BindDataSource;
import jef.database.cache.KeyDimension;
import jef.database.dialect.ColumnType;
import jef.database.dialect.DatabaseDialect;
import jef.database.dialect.type.ColumnMapping;
import jef.database.query.DbTable;
import jef.database.query.JpqlExpression;
import jef.database.query.PKQuery;
import jef.database.wrapper.clause.BindSql;
import jef.tools.JefConfiguration;
import jef.tools.StringUtils;

/**
 * 抽象类用于简化Tablemeta的实现
 * 
 * @author jiyi
 * 
 */
public abstract class MetadataAdapter implements ITableMetadata {
	protected String schema;
	protected String tableName;
	private String bindDsName;
	protected List<ColumnMapping<?>> metaFields;
	final List<jef.database.annotation.Index> indexMap = new ArrayList<jef.database.annotation.Index>(5);//记录对应表的所有索引，当建表时使用可自动创建索引
	protected Field[] lobNames;
	
	protected final Map<Field, ColumnMapping<?>> schemaMap = new IdentityHashMap<Field, ColumnMapping<?>>();
	
	protected abstract Collection<ColumnMapping<?>> getColumnSchema();


	public Field[] getLobFieldNames() {
		return lobNames;
	}

	protected void initByAnno(Class<?> thisType,javax.persistence.Table table,BindDataSource bindDs) {
		// schema初始化
		if (table != null) {
			if (table.schema().length() > 0) {
				schema = MetaHolder.getMappingSchema(table.schema());// 重定向
			}
			if (table.name().length() > 0) {
				tableName = table.name();
			}
		}
		if (tableName == null) {
			// 表名未指定，缺省生成
			boolean needTranslate = JefConfiguration.getBoolean(DbCfg.TABLE_NAME_TRANSLATE, false);
			if (needTranslate) {
				tableName = DbUtils.upperToUnderline(thisType.getSimpleName());
			} else {
				tableName = thisType.getSimpleName();
			}
		}
		if (bindDs != null) {
			this.bindDsName = MetaHolder.getMappingSite(StringUtils.trimToNull(bindDs.value()));
		}
	}
	
	public String getBindDsName() {
		return bindDsName;
	}

	public void setBindDsName(String bindDsName) {
		this.bindDsName = MetaHolder.getMappingSite(bindDsName);
		this.bindProfile=null;
	}
	
	public List<ColumnMapping<?>> getMetaFields() {
		if (metaFields == null) {
			Collection<ColumnMapping<?>> map = this.getColumnSchema();
			ColumnMapping<?>[] fields = map.toArray(new ColumnMapping[map.size()]);
			Arrays.sort(fields, new Comparator<ColumnMapping<?>>() {
				public int compare(ColumnMapping<?> field1, ColumnMapping<?> field2) {
					Class<? extends ColumnType> type1 = field1.get().getClass();
					Class<? extends ColumnType> type2 = field2.get().getClass();
					Boolean b1 = (type1 == ColumnType.Blob.class || type1 == ColumnType.Clob.class);
					Boolean b2 = (type2 == ColumnType.Blob.class || type2 == ColumnType.Clob.class);
					return b1.compareTo(b2);
				}
			});
			metaFields = Arrays.asList(fields);
		}
		return metaFields;
	}

	public String getSchema() {
		return schema;
	}

	/**
	 * 返回表名
	 * 
	 * @param withSchema
	 *            true要求带schema
	 * @return
	 */
	public String getTableName(boolean withSchema) {
		if (withSchema && schema != null)
			return new StringBuilder(schema.length() + tableName.length() + 1).append(schema).append('.').append(tableName).toString();
		return tableName;
	}
	
	public String getColumnName(Field fld, DatabaseDialect profile, boolean escape) {
		ColumnMapping<?> mType = this.schemaMap.get(fld);
		if (mType != null) {
			return mType.getColumnName(profile, escape);
		}
		//意外情况
		if (fld instanceof JpqlExpression) {
			throw new UnsupportedOperationException();
		}
		String name = profile.getColumnNameToUse(fld.name());
		return escape?DbUtils.escapeColumn(profile, name):name; 
	}
	
	public String getColumnName(Field fld, String alias, DatabaseDialect profile) {
		if (alias != null) {
			if (fld instanceof JpqlExpression) {
				throw new UnsupportedOperationException();
			} else {
				StringBuilder sb = new StringBuilder();
				sb.append(alias).append('.').append(getColumnName(fld, profile, true));
				return sb.toString();
			}
		} else {
			return getColumnName(fld, profile, true);
		}
	}
	
	private DbTable cachedTable;
	private DatabaseDialect bindProfile;
	protected KeyDimension pkDim;
	
	public DbTable getBaseTable(DatabaseDialect profile){
		if(bindProfile!=profile){
			synchronized (this) {
				initCache(profile);
			}
		}
		return cachedTable; 
	}

	private void initCache(DatabaseDialect profile) {
		bindProfile=profile;
		cachedTable=new DbTable(bindDsName, profile.getObjectNameToUse(getTableName(true)),false,false);
	}


	public KeyDimension getPKDimension(List<Serializable> pks,DatabaseDialect profile) {
		if(pkDim==null){
			PKQuery<?> query=new PKQuery<IQueryableEntity>(this, pks,instance());
			BindSql sql = query.toPrepareWhereSql(null, profile);
			KeyDimension dim = new KeyDimension(sql.getSql(), null);
			pkDim=dim;
		}
		return pkDim;
	}
}
