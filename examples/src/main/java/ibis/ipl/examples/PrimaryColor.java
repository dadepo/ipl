/*
 * Copyright 2010 Vrije Universiteit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ibis.ipl.examples;

/**
 * This would be not be successfully serialized
 */
public enum PrimaryColor {
    RED {
        public String toStuff(long d)  { return "stuff"; }
    },
    BLUE {
        public String toStuff(long d)  { return "stuff"; }
    },
    GREEN {
        public String toStuff(long d)  { return "stuff"; }
    }
}

/**
 * This would be successfully serialized
 */
//public enum PrimaryColor {
//    RED,
//    BLUE,
//    GREEN
//}