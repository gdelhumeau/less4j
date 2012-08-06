package org.porting.less4j.core.ast;

import org.porting.less4j.core.parser.HiddenTokenAwareTree;

//FIXME: this does not handle parametrized classes yet
//FIXME: this does not handle :: correctly
//FIXME: not done yet
public class Pseudo extends ASTCssNode {

  private String name;
  private String parameter;

  public Pseudo(HiddenTokenAwareTree token, String name) {
    this(token, name, null);
  }
  
  public Pseudo(HiddenTokenAwareTree token, String name, String parameter) {
    super(token);
    this.name = name;
    this.parameter = parameter;
  }

  public String getName() {
    return name;
  }

  @Override
  public ASTCssNodeType getType() {
    return ASTCssNodeType.PSEUDO;
  }

  public boolean hasParameters() {
    return getParameter()!=null;
  }

  public String getParameter() {
    return parameter;
  }

}