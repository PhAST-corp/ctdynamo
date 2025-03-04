grammar Dynamo;

@header {
    import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
    import java.util.List;
}

expression returns [ AttributeValue value ] : body=andOr EOF { $value = $body.value; } ;

update : updateSequence EOF ;

updateSequence: updateSequence updatePart
  | updatePart
  ;

updatePart: 'SET' setTerms
  | 'REMOVE' removeTerms
  ;

setTerms: setTerms ',' setTerm
  | setTerm
  ;

setTerm: lvalAtom '=' andOr;

removeTerms: removeTerms ',' lvalAtom
  | lvalAtom
  ;

andOr returns [ AttributeValue value ]
  : left=andOr 'AND' right=not { $value = ExpressionEvaluator.makeBool(ExpressionEvaluator.unpackBool($left.value) && ExpressionEvaluator.unpackBool($right.value)); }
  | left=andOr 'OR' right=not { $value = ExpressionEvaluator.makeBool(ExpressionEvaluator.unpackBool($left.value) || ExpressionEvaluator.unpackBool($right.value)); }
  | notVal=not { $value = $notVal.value; }
  ;

not returns [AttributeValue value ] : 'NOT' notVal=not { $value = ($notVal.value == null ? null : ExpressionEvaluator.makeBool(!ExpressionEvaluator.unpackBool($notVal.value))); }
  | betweenVal=between { $value = $betweenVal.value; }
  ;

between returns [ AttributeValue value ]
  : midVal=addSub 'BETWEEN' loVal=addSub 'AND' hiVal=addSub { $value = ExpressionEvaluator.makeBool(
      AttributeComparator.INSTANCE.compare($loVal.value, $midVal.value) <= 0
      && AttributeComparator.INSTANCE.compare($midVal.value, $hiVal.value) <= 0); }
  | addSub 'IN' '(' tuple ')' { ExpressionEvaluator.unsupported("IN"); }
  | compareVal=compare { $value = $compareVal.value; }
  ;

tuple returns [ List<AttributeValue> values ]
  : tupleVal=tuple ',' andOrVal=andOr { $values = new ArrayList<>($tupleVal.values); $values.add($andOrVal.value); }
  | andOrVal=andOr { $values = List.of($andOrVal.value); }
  ;

compare returns [ AttributeValue value ]
  : left=addSub '=' right=addSub { $value = ExpressionEvaluator.makeBool(ExpressionEvaluator.attributeEquals($left.value, $right.value)); }
  | left=addSub '<>' right=addSub { $value = ExpressionEvaluator.makeBool(!ExpressionEvaluator.attributeEquals($left.value, $right.value)); }
  | left=addSub '<' right=addSub { $value = ExpressionEvaluator.makeBool(AttributeComparator.INSTANCE.compare($left.value, $right.value) < 0); }
  | left=addSub '<=' right=addSub { $value = ExpressionEvaluator.makeBool(AttributeComparator.INSTANCE.compare($left.value, $right.value) <= 0); }
  | left=addSub '>' right=addSub { $value = ExpressionEvaluator.makeBool(AttributeComparator.INSTANCE.compare($left.value, $right.value) > 0); }
  | left=addSub '>=' right=addSub { $value = ExpressionEvaluator.makeBool(AttributeComparator.INSTANCE.compare($left.value, $right.value) >= 0); }
  | addSubVal=addSub { $value = $addSubVal.value; }
  ;

addSub returns [ AttributeValue value ]
  : left=addSub '+' right=mulDiv { $value = ExpressionEvaluator.addOrSub($left.value, $right.value, true); }
  | left=addSub '-' right=mulDiv { $value = ExpressionEvaluator.addOrSub($left.value, $right.value, false); }
  | mulDivVal = mulDiv { $value = $mulDivVal.value; }
  ;

mulDiv returns [ AttributeValue value ] : mulDiv '*' func { ExpressionEvaluator.unsupported("*"); }
  | mulDiv '/' func { ExpressionEvaluator.unsupported("/"); }
  | funcVal=func { $value = $funcVal.value; }
  ;

func returns [ AttributeValue value ]
  : 'attribute_exists' '(' dotVal=dot ')' { $value = ExpressionEvaluator.attributeExists($dotVal.value); }
  | 'attribute_not_exists' '(' dotVal=dot ')' { $value = ExpressionEvaluator.attributeNotExists($dotVal.value); }
  | 'attribute_type' '(' dot ')' { ExpressionEvaluator.unsupported("attribute_type"); }
  | 'contains' '(' dotVal=dot ',' andOrVal=andOr ')' { $value = ExpressionEvaluator.contains($dotVal.value, $andOrVal.value); }
  | 'begins_with' '(' andOrVal=andOr ',' andOrVal2=andOr ')' { $value = ExpressionEvaluator.beginsWith($andOrVal.value, $andOrVal2.value); }
  | 'size' '(' andOrVal=andOr ')' { $value = ExpressionEvaluator.size($andOrVal.value); }
  | 'if_not_exists' '(' atomVal=atom ',' andOrVal=andOr ')' { $value = ($atomVal.value == null ? $andOrVal.value : $atomVal.value); }
  | 'list_append' '(' andOrVal=andOr ',' andOrVal2=andOr ')' { $value = ExpressionEvaluator.listAppend($andOrVal.value, $andOrVal2.value); }
  | dotVal=dot { $value = $dotVal.value; }
  ;

dot returns [ AttributeValue value ] : map=dot '.' subPart=ATTRIBUTE { $value = ($map.value == null ? null : $map.value.m().get($subPart.text)); }
  | parenVal=paren { $value = $parenVal.value; }
  ;

paren returns [ AttributeValue value ] : '(' parenVal=andOr ')' { $value = $parenVal.value; }
  | atom { $value = $atom.value; }
  ;

atom returns [ AttributeValue value ] : ar=attributeRefAtom { $value = $ar.value; }
  | vr=valueRefAtom { $value = $vr.value; }
  | a=attributeAtom { $value = $a.value; } ;

lvalAtom returns [ String value ]
  : ATTRIBUTE
  | ATTRIBUTE_REF
  ;

attributeRefAtom returns [ AttributeValue value ] : ATTRIBUTE_REF ;

valueRefAtom returns [ AttributeValue value ] : VALUE_REF ;

attributeAtom returns [ AttributeValue value ] : ATTRIBUTE ;

ATTRIBUTE_REF : '#' ID ;

VALUE_REF : ':' ID ;

ATTRIBUTE : ID ;

fragment ID : [a-zA-Z_][a-zA-Z_0-9]* ;

WS : [ \r\t\n]+ -> skip ;

BAD_LEXEME : . ;