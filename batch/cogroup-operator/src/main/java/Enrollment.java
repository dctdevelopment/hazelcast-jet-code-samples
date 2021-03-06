/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.Serializable;

/**
 * A DTO for student enrollment.
 * <p>
 * For the sake of simplicity we use {@link Serializable} here. For better
 * performance we should have used Hazelcast Custom Serialization.
 */
 class Enrollment implements Serializable {

    private final String student;
    private final String course;

    Enrollment(String student, String course) {
        this.course = course;
        this.student = student;
    }

    public String getStudent() {
        return student;
    }

    public String getCourse() {
        return course;
    }

    @Override
    public String toString() {
        return "Enrollment{" +
                "student='" + student + '\'' +
                ", course=" + course +
                '}';
    }

}
