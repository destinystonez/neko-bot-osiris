package org.nekotori.sd;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Text2ImgResponse {

    @JsonProperty("images")
    private List<String> images;

    @JsonProperty("parameters")
    private Text2ImgOptions parameters;

    @JsonProperty("info")
    private String info;

}
