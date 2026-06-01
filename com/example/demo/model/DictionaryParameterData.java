package com.example.demo.model;

public class DictionaryParameterData {
    public final String useCaseName;
    public final String parameterName;
    public final String parameterFormat;
    public final boolean required;
    public final String description;
    public final String paramValue;

    public DictionaryParameterData(String useCaseName, String parameterName, String parameterFormat,
                                    boolean required, String description, String paramValue) {
        this.useCaseName = useCaseName == null ? "" : useCaseName.trim();
        this.parameterName = parameterName == null ? "" : parameterName.trim();
        this.parameterFormat = parameterFormat == null ? "" : parameterFormat.trim();
        this.required = required;
        this.description = description == null ? "" : description.trim();
        this.paramValue = paramValue == null ? "" : paramValue.trim();
    }
}
