<#if package??>package ${package};</#if>
public class UpdateClass implements rubah.ConversionClass {
<#list classes as c>

<#if c.hasNonStaticChanges>
	public void ${convertName}(${c.v0} o0, ${c.v1} o1) {
		// New fields<#list c.newFields as f><#if !modifier.isStatic(f.f.getAccess())>
		o1.${f.f.name} = ${helper.getNullValue(f.f.getType())};</#if></#list>
		
		// Fields with type changed<#list c.typeChangedFields as f><#if !modifier.isStatic(f.f.getAccess())>
		o1.${f.f.name} = ${helper.getNullValue(f.f.getType())};</#if></#list>
		
		return;
	}
</#if>

<#if c.hasStaticChanges>
	// ${c.v0} to ${c.v1}
	public void ${convertStaticName}(${c.v1} ignore) {
		${c.v1}.${copyStaticFieldsMethodName}();
		
		// New fields<#list c.newFields as f><#if modifier.isStatic(f.f.getAccess())>
		${c.v1}.${f.f.name} = ${helper.getNullValue(f.f.getType())};</#if></#list>
		
		// New constants<#list c.newConstants as f><#if modifier.isStatic(f.f.getAccess())>
		${c.v1}.${f.f.name} = ${helper.getNullValue(f.f.getType())};</#if></#list>
		
		// Fields with type changed<#list c.typeChangedFields as f><#if modifier.isStatic(f.f.getAccess())>
		${c.v1}.${f.f.name} = null;</#if></#list>
	}
</#if>
</#list>
}
