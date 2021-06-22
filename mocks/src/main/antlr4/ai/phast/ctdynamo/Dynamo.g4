grammar Dynamo;

@header {
    import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
    import java.util.List;
}

expression returns [ AttributeValue value ] : body=andOr EOF { $value = $body.value; } ;

andOr returns [ AttributeValue value ]
  : left=andOr 'AND' right=not { $value = ExpressionEvaluator.makeBool(ExpressionEvaluator.unpackBool($left.value) && ExpressionEvaluator.unpackBool($right.value)); }
  | left=andOr 'OR' right=not { $value = ExpressionEvaluator.makeBool(ExpressionEvaluator.unpackBool($left.value) || ExpressionEvaluator.unpackBool($right.value)); }
  | notVal=not { $value = $notVal.value; }
  ;

not returns [AttributeValue value ] : 'NOT' notVal=not { $value = ExpressionEvaluator.makeBool(!ExpressionEvaluator.unpackBool($notVal.value)); }
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
  : left=addSub '=' right=addSub { $value = ExpressionEvaluator.makeBool(AttributeComparator.INSTANCE.compare($left.value, $right.value) == 0); }
  | left=addSub '<>' right=addSub { $value = ExpressionEvaluator.makeBool(AttributeComparator.INSTANCE.compare($left.value, $right.value) != 0); }
  | left=addSub '<' right=addSub { $value = ExpressionEvaluator.makeBool(AttributeComparator.INSTANCE.compare($left.value, $right.value) < 0); }
  | left=addSub '<=' right=addSub { $value = ExpressionEvaluator.makeBool(AttributeComparator.INSTANCE.compare($left.value, $right.value) <= 0); }
  | left=addSub '>' right=addSub { $value = ExpressionEvaluator.makeBool(AttributeComparator.INSTANCE.compare($left.value, $right.value) > 0); }
  | left=addSub '>=' right=addSub { $value = ExpressionEvaluator.makeBool(AttributeComparator.INSTANCE.compare($left.value, $right.value) >= 0); }
  | addSubVal=addSub { $value = $addSubVal.value; }
  ;

addSub returns [ AttributeValue value ] : addSub '+' mulDiv { ExpressionEvaluator.unsupported("+"); }
  | addSub '-' mulDiv { ExpressionEvaluator.unsupported("-"); }
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
  | 'contains' '(' dot ',' andOr ')' { ExpressionEvaluator.unsupported("contains"); }
  | 'begins_with' '(' andOr ',' andOr ')' { ExpressionEvaluator.unsupported("begins_with"); }
  | 'size' '(' andOr ')' { ExpressionEvaluator.unsupported("size"); }
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

attributeRefAtom returns [ AttributeValue value ] : ATTRIBUTE_REF ;

valueRefAtom returns [ AttributeValue value ] : VALUE_REF ;

attributeAtom returns [ AttributeValue value ] : ATTRIBUTE ;

ATTRIBUTE_REF : '#' ID ;

VALUE_REF : ':' ID ;

ATTRIBUTE : ID ;

fragment ID : [a-zA-Z][a-zA-Z0-9]* ;

WS : [ \r\t\n]+ -> skip ;

BAD_LEXEME : . ;