<#ftl output_format="HTML">
<#macro configurationEditor 
			name 
			id 
			label 
			description
			type 
			hasEnableSwitch 
			isEnabled 
			currentValue 
			allowedValues=[]
			allowedValuesLabels=[]
			customValuesAllowed=false
			fixed=false 
			schema=""
			labelClass="none">
	<#assign editorType = type>
	<#assign disabled = (hasEnableSwitch && !isEnabled) ?then(' disabled="disabled" ',"")>
	<#assign readonly = (fixed) ?then(' readonly="readonly" ',"")>
	<#if editorType=="text">
		<#if allowedValues?size == 2 && allowedValues?sort?join("")?upper_case == "FALSETRUE">
			<#assign editorType="select">
		<#elseif 1<allowedValues?size>
			<#assign editorType="select">
		</#if>
	</#if>
	<div class="form-group">
		<label class="control-label col-sm-3 text-${labelClass}">${label}</label>
		<div class="col-sm-9">
			<#if hasEnableSwitch || fixed>
				<div class="input-group">
			</#if>
				<#if hasEnableSwitch>
					<span class="input-group-addon">
						<input type="checkbox" <#if isEnabled> checked="checked"</#if> onchange="document.getElementById('${id}').disabled=!this.checked;" id="enable.${id}" name="enable.${name}" value="true" title="Check this box to make the value entered for this data item fixed"/>
					</span>
				</#if>

				<#switch editorType>
					<#case "boolean">
						<#if hasEnableSwitch && !isEnabled>
							<span class="text-danger">Currently the editor for boolean types cannot be disabled.</span>
						</#if>
						<div class="checkboxOne">
							<#assign booleanValue = (0<currentValue?length)?then(currentValue,"false")>
							<input type="checkbox" class="checkboxOne" value="true" id="${id}" name="${name}" <#if booleanValue?boolean>checked="checked"</#if> />
							<label for="${id}"></label>
							<div></div>
						</div>
						<#-- TODO: nur wenn die Server-seitige Lösung für Checkboxes nicht geht, das hier wieder nutzen <input type="hidden" value="false" name="${name}" /> -->
						<#break>
					<#case "text">
					<#case "date">
					<#case "time">
					<#case "datetime-local">
					<#case "datetime">
					<#case "password">
					<#case "url">
					<#case "number">
					<#case "color">
						<input class="form-control col-sm-9" type="${editorType}" id="${id}" name="${name}" value="${currentValue}" ${disabled} ${readonly}/>
						<#break>
					<#case "duration">
						<select class="form-control selectpicker" style="width:100%" id="${id}" name="${name}" ${disabled}>
							<optgroup label="below 1 minute">
								<option <#if currentValue=="PT10S"> selected="selected"</#if> value="PT10S">10 seconds</option>
								<option <#if currentValue=="PT20S"> selected="selected"</#if> value="PT20S">20 seconds</option>
								<option <#if currentValue=="PT30S"> selected="selected"</#if> value="PT30S">30 seconds</option>
								<option <#if currentValue=="PT45S"> selected="selected"</#if> value="PT45S">45 seconds</option>
							</optgroup>
							<optgroup label="below 1 hour">
								<option <#if currentValue=="PT1M"> selected="selected"</#if> value="PT1M">1 minute</option>
								<option <#if currentValue=="PT2M"> selected="selected"</#if> value="PT2M">2 minutes</option>
								<option <#if currentValue=="PT3M"> selected="selected"</#if> value="PT3M">3 minutes</option>
								<option <#if currentValue=="PT5M"> selected="selected"</#if> value="PT5M">5 minutes</option>
								<option <#if currentValue=="PT10M"> selected="selected"</#if> value="PT10M">10 minutes</option>
								<option <#if currentValue=="PT15M"> selected="selected"</#if> value="PT15M">15 minutes</option>
								<option <#if currentValue=="PT20M"> selected="selected"</#if> value="PT20M">20 minutes</option>
								<option <#if currentValue=="PT30M"> selected="selected"</#if> value="PT30M">30 minutes</option>
								<option <#if currentValue=="PT45M"> selected="selected"</#if> value="PT45M">45 minutes</option>
							</optgroup>
							<optgroup label="1 hour and more">
								<option <#if currentValue=="PT1H"> selected="selected"</#if> value="PT1H">1 hour</option>
								<option <#if currentValue=="PT2H"> selected="selected"</#if> value="PT2H">2 hours</option>
								<option <#if currentValue=="PT3H"> selected="selected"</#if> value="PT3H">3 hours</option>
								<option <#if currentValue=="PT6H"> selected="selected"</#if> value="PT6H">6 hours</option>
								<option <#if currentValue=="PT8H"> selected="selected"</#if> value="PT8H">8 hours</option>
								<option <#if currentValue=="PT12H"> selected="selected"</#if> value="PT12H">12 hours</option>
								<option <#if currentValue=="PT1D"> selected="selected"</#if> value="PT24H">1 day</option>
							</optgroup>
						</select> 
						<#break>
					<#case "select">
					<#case "enum">
						<#if !fixed>
							<select style="width:100%" class="form-control selectpicker <#if 20<allowedValues?size>with-search</#if> <#if customValuesAllowed>custom-values-allowed</#if>" id="${id}" name="${name}" ${disabled}>
								<#list allowedValues as allowedValue>
									<option <#if currentValue==allowedValue>selected="selected"</#if> value="${allowedValue}"><#if allowedValuesLabels?size==allowedValues?size>${allowedValuesLabels[allowedValue?index]}<#else>${allowedValue}</#if></option>
								</#list>
								<#if !allowedValues?seq_contains(currentValue)>
									<option selected="selected" value="${currentValue}">${currentValue}</option>
								</#if>
							</select>
						<#else>
							<input class="form-control col-sm-9" type="text" id="${id}" <#--name="${name}"--> value="${currentValue}" ${readonly}/>
						</#if> 
						<#break>
					<#case "json">
						<#if hasEnableSwitch && !isEnabled>
							<span class="text-danger">Currently the editor for JSON values cannot be disabled.</span>
						</#if>
						<#assign value = (currentValue?? && 0<currentValue?length) ?then(currentValue,"{}")>
						<input type="hidden" id="hidden_${id}" name="${name}" value="${value}"/>
						<input type="hidden" id="schema_${id}" value="${schema}"/>
						<div class="jsoneditor" id="editor_${id}"></div>
						<script type="text/javascript">
							installJsonEditor('${id}');
						</script>
						<#break>
					<#case "sql">
						<div class="jumbotron editor syntaxhighlighting" id="${id}">${currentValue}</div>
						<input type="hidden" id="hidden_${id}" name="${name}" value="${currentValue}"/>
						<script>
							installSyntaxEditor('${id}', 'sql');
						</script>
						<#break>
						<#default>
						<#if editorType?starts_with("value_")>
							<#if hasEnableSwitch && !isEnabled>
								<span class="text-danger">Currently the editor for OversigtValues cannot be disabled.</span>
							</#if>
							<select class="form-control selectpicker no-search" id="${id}" name="${name}">
								<#assign allowedValues=values[editorType[6..]]>
								<option<#if currentValue=="0" || currentValue==""> selected="selected"</#if> value="0">&nbsp;</option>
								<#list values[editorType[6..]] as value>
								<option<#if currentValue==value.id?c> selected="selected"</#if> value="${value.id}">${value.name}</option>
								</#list>
							</select>
						<#else>
							<input class="form-control col-sm-9" type="text" id="${id}" name="${name}" value="${currentValue}" ${disabled}/>
						</#if>
						<#break>												
				</#switch>
			<#if hasEnableSwitch || fixed>
				<#if fixed>
					<span class="input-group-addon">fixed</span>
				</#if>
				</div>
			</#if>
			<#if description??>
				<span class="help-block">${description}</span>
			</#if>
		</div>
	</div>
</#macro>
